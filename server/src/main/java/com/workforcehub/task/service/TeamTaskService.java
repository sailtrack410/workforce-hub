package com.workforcehub.task.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.dto.TeamTaskSaveRequest;
import com.workforcehub.task.dto.TeamTaskListResponse;
import com.workforcehub.task.dto.TeamTaskSubtaskRequest;
import com.workforcehub.task.dto.TeamTaskSubtaskResponse;
import com.workforcehub.task.repository.TeamTaskRepository;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.Objects;

/**
 * 团队任务服务。
 */
@Service
public class TeamTaskService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE");

    private final TeamTaskRepository teamTaskRepository;

    public TeamTaskService(TeamTaskRepository teamTaskRepository) {
        this.teamTaskRepository = teamTaskRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse create(TeamTaskSaveRequest request) {
        Long creatorId = requireEmployeeId();
        validateAssignment(request, creatorId);
        validateList(request.getGroupId(), null, creatorId);
        TeamTaskResponse task = new TeamTaskResponse();
        task.setTaskId(IdGenerator.nextId());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setAssigneeId(request.getAssigneeId());
        task.setCreatorId(creatorId);
        task.setStatus("TODO");
        task.setStartDate(request.getStartDate());
        task.setEndDate(request.getEndDate());
        task.setGroupId(request.getGroupId());
        teamTaskRepository.insert(task);
        teamTaskRepository.activity(task.getTaskId(), creatorId, "CREATED", "创建了任务");
        return detail(task.getTaskId());
    }

    public List<TeamTaskResponse> list(Long assigneeId, String status, java.time.LocalDate start, java.time.LocalDate end, String scope) {
        Long userId = requireEmployeeId();
        if (StringUtils.hasText(status) && !ALLOWED_STATUSES.contains(status)) {
            throw new BizException(400, "无效的任务状态");
        }
        if (!Set.of("ALL", "ASSIGNED", "CREATED").contains(scope)) {
            throw new BizException(400, "无效的任务范围");
        }
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(400, "开始日期不能晚于结束日期");
        }
        List<TeamTaskResponse> tasks = teamTaskRepository.find(userId, assigneeId, status, start, end, scope);
        tasks.forEach(task -> fillCapabilities(task, userId));
        teamTaskRepository.fillSummaries(tasks, userId);
        return tasks;
    }

    public List<OrgUserSummaryResponse> assignees() {
        return teamTaskRepository.findAssignees(requireEmployeeId());
    }

    public TeamTaskResponse detail(Long taskId) {
        TeamTaskResponse task = requireTask(taskId);
        task.setGroupName(teamTaskRepository.listName(task.getGroupId()));
        task.setWatching(teamTaskRepository.watching(taskId, CurrentUserHolder.requireUserId()));
        task.setWatcherNames(teamTaskRepository.watcherNames(taskId));
        task.setSubtasks(teamTaskRepository.subtasks(taskId));
        task.setActivities(teamTaskRepository.activities(taskId));
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse update(Long taskId, TeamTaskSaveRequest request) {
        TeamTaskResponse existing = requireTask(taskId);
        if (!existing.getCreatorId().equals(CurrentUserHolder.requireUserId())) {
            throw new BizException(403, "仅创建人可编辑团队任务");
        }
        validateAssignment(request, existing.getCreatorId());
        validateList(request.getGroupId(), existing.getGroupId(), existing.getCreatorId());
        Long expectedAssigneeId = existing.getAssigneeId();
        String expectedStatus = existing.getStatus();
        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setAssigneeId(request.getAssigneeId());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setGroupId(request.getGroupId());
        if (!expectedAssigneeId.equals(request.getAssigneeId())) {
            existing.setStatus("TODO");
        }
        if (teamTaskRepository.update(existing, expectedAssigneeId, expectedStatus) == 0) {
            throw new BizException(409, "任务已变更，请刷新后重试");
        }
        teamTaskRepository.activity(taskId, CurrentUserHolder.requireUserId(), "UPDATED", "更新了任务信息");
        return detail(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse updateStatus(Long taskId, String status) {
        if (status == null || !Set.of("TODO", "IN_PROGRESS", "DONE").contains(status)) {
            throw new BizException(400, "无效的任务状态");
        }
        TeamTaskResponse existing = requireTask(taskId);
        Long userId = CurrentUserHolder.requireUserId();
        if (!userId.equals(existing.getAssigneeId())) {
            throw new BizException(403, "仅负责人可更新任务执行状态");
        }
        if (status.equals(existing.getStatus())) {
            return detail(taskId);
        }
        boolean allowed = switch (existing.getStatus()) {
            case "TODO" -> Set.of("IN_PROGRESS", "DONE").contains(status);
            case "IN_PROGRESS" -> Set.of("TODO", "DONE").contains(status);
            case "DONE" -> "TODO".equals(status);
            // 保留历史评审中任务的退出路径，新操作不再进入评审中。
            case "IN_REVIEW" -> true;
            default -> false;
        };
        if (!allowed) {
            throw new BizException(409, "当前任务状态不支持此操作");
        }
        if (teamTaskRepository.updateStatus(taskId, userId, existing.getStatus(), status) == 0) {
            requireTask(taskId);
            throw new BizException(409, "任务已变更，请刷新后重试");
        }
        teamTaskRepository.activity(taskId, userId, "STATUS", switch (status) {
            case "DONE" -> "完成了任务";
            case "IN_PROGRESS" -> "开始了任务";
            default -> "将任务设为待开始";
        });
        return detail(taskId);
    }

    private void validateAssignment(TeamTaskSaveRequest request, Long creatorId) {
        if (request.getAssigneeId() == null || request.getAssigneeId() <= 0) {
            throw new BizException(400, "负责人员工ID必须为正数");
        }
        if (creatorId.equals(request.getAssigneeId())) {
            throw new BizException(400, "个人事项请使用个人待办");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BizException(400, "任务起止日期不能为空");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BizException(400, "开始日期不能晚于结束日期");
        }
        if (!teamTaskRepository.canAssign(creatorId, request.getAssigneeId())) {
            throw new BizException(403, "只能派发给有效的同部门同事或直属下属");
        }
    }

    private void fillCapabilities(TeamTaskResponse task, Long userId) {
        boolean creator = userId.equals(task.getCreatorId());
        boolean assignee = userId.equals(task.getAssigneeId());
        task.setCanEdit(creator);
        task.setCanDelete(creator);
        task.setCanStart(assignee && Set.of("TODO", "IN_REVIEW").contains(task.getStatus()));
        task.setCanComplete(assignee && Set.of("TODO", "IN_PROGRESS", "IN_REVIEW").contains(task.getStatus()));
        task.setCanReopen(assignee && "DONE".equals(task.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long taskId) {
        TeamTaskResponse existing = requireTask(taskId);
        Long userId = CurrentUserHolder.requireUserId();
        if (!existing.getCreatorId().equals(userId)) {
            throw new BizException(403, "仅创建人可删除团队任务");
        }
        if (teamTaskRepository.deleteById(taskId, userId) == 0) {
            throw new BizException(404, "团队任务不存在");
        }
        teamTaskRepository.deleteCollaboration(taskId);
    }

    public List<TeamTaskListResponse> lists() {
        return teamTaskRepository.lists(requireEmployeeId());
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskListResponse saveList(Long listId, String name) {
        Long userId = requireEmployeeId();
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.length() > 64) throw new BizException(400, "清单名称须为1至64个字符");
        boolean create = listId == null;
        if (create) listId = IdGenerator.nextId();
        else if (!teamTaskRepository.listById(listId, userId).orElseThrow(() -> new BizException(404, "清单不存在")).canEdit()) {
            throw new BizException(403, "仅创建人可修改清单");
        }
        teamTaskRepository.saveList(listId, normalized, userId, create);
        return teamTaskRepository.listById(listId, userId).orElseThrow(() -> new BizException(404, "清单不存在"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteList(Long listId) {
        Long userId = requireEmployeeId();
        if (!teamTaskRepository.listById(listId, userId).orElseThrow(() -> new BizException(404, "清单不存在")).canEdit()) {
            throw new BizException(403, "仅创建人可删除清单");
        }
        if (!teamTaskRepository.deleteEmptyList(listId, userId)) throw new BizException(409, "请先移出清单中的任务");
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse watch(Long taskId, boolean watching) {
        teamTaskRepository.lockTask(taskId);
        requireTask(taskId);
        teamTaskRepository.watch(taskId, CurrentUserHolder.requireUserId(), watching);
        return detail(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse comment(Long taskId, String content) {
        teamTaskRepository.lockTask(taskId);
        requireTask(taskId);
        String text = content == null ? "" : content.strip();
        if (text.isEmpty() || text.length() > 2000) throw new BizException(400, "评论须为1至2000个字符");
        teamTaskRepository.activity(taskId, CurrentUserHolder.requireUserId(), "COMMENT", text);
        return detail(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse addSubtask(Long taskId, TeamTaskSubtaskRequest request) {
        teamTaskRepository.lockTask(taskId);
        TeamTaskResponse task = requireTask(taskId);
        if (!task.isCanEdit()) throw new BizException(403, "仅创建人可添加子任务");
        String title = subtaskTitle(request.title());
        teamTaskRepository.addSubtask(IdGenerator.nextId(), taskId, title);
        teamTaskRepository.activity(taskId, CurrentUserHolder.requireUserId(), "SUBTASK", "添加子任务：" + title);
        return detail(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse updateSubtask(Long taskId, Long subtaskId, TeamTaskSubtaskRequest request) {
        teamTaskRepository.lockTask(taskId);
        TeamTaskResponse task = requireTask(taskId);
        TeamTaskSubtaskResponse existing = teamTaskRepository.subtasks(taskId).stream()
            .filter(item -> item.subtaskId().equals(subtaskId)).findFirst().orElseThrow(() -> new BizException(404, "子任务不存在"));
        String title = subtaskTitle(request.title());
        boolean titleChanged = !existing.title().equals(title);
        boolean statusChanged = existing.completed() != request.completed();
        if (titleChanged && !task.isCanEdit()) throw new BizException(403, "仅创建人可编辑子任务标题");
        if (statusChanged && !CurrentUserHolder.requireUserId().equals(task.getAssigneeId())) throw new BizException(403, "仅负责人可完成或重开子任务");
        if (teamTaskRepository.updateSubtask(taskId, subtaskId, title, request.completed()) == 0) throw new BizException(409, "子任务已变更");
        if (titleChanged || statusChanged) teamTaskRepository.activity(taskId, CurrentUserHolder.requireUserId(), "SUBTASK",
            (statusChanged ? request.completed() ? "完成子任务：" : "重开子任务：" : "更新子任务：") + title);
        return detail(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse deleteSubtask(Long taskId, Long subtaskId) {
        teamTaskRepository.lockTask(taskId);
        if (!requireTask(taskId).isCanEdit()) throw new BizException(403, "仅创建人可删除子任务");
        if (teamTaskRepository.deleteSubtask(taskId, subtaskId) == 0) throw new BizException(404, "子任务不存在");
        teamTaskRepository.activity(taskId, CurrentUserHolder.requireUserId(), "SUBTASK", "删除了子任务");
        return detail(taskId);
    }

    private String subtaskTitle(String title) {
        String normalized = title == null ? "" : title.strip();
        if (normalized.isEmpty() || normalized.length() > 128) throw new BizException(400, "子任务标题须为1至128个字符");
        return normalized;
    }

    private void validateList(String groupId, String previousGroupId, Long creatorId) {
        if (groupId == null || Objects.equals(groupId, previousGroupId)) return;
        if (teamTaskRepository.listOwner(groupId).filter(owner -> !owner.equals(creatorId)).isPresent()) {
            throw new BizException(403, "只能将任务添加到自己创建的清单");
        }
    }

    private TeamTaskResponse requireTask(Long taskId) {
        Long userId = requireEmployeeId();
        TeamTaskResponse task = teamTaskRepository.findById(taskId)
            .filter(candidate -> userId.equals(candidate.getCreatorId()) || userId.equals(candidate.getAssigneeId()))
            .orElseThrow(() -> new BizException(404, "团队任务不存在"));
        fillCapabilities(task, userId);
        return task;
    }

    private Long requireEmployeeId() {
        Long userId = CurrentUserHolder.requireUserId();
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持团队任务");
        }
        return userId;
    }
}
