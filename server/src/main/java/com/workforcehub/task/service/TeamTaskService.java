package com.workforcehub.task.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.dto.TeamTaskSaveRequest;
import com.workforcehub.task.repository.TeamTaskRepository;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

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
        return requireTask(task.getTaskId());
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
        return tasks;
    }

    public List<OrgUserSummaryResponse> assignees() {
        return teamTaskRepository.findAssignees(requireEmployeeId());
    }

    public TeamTaskResponse detail(Long taskId) {
        return requireTask(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse update(Long taskId, TeamTaskSaveRequest request) {
        TeamTaskResponse existing = requireTask(taskId);
        if (!existing.getCreatorId().equals(CurrentUserHolder.requireUserId())) {
            throw new BizException(403, "仅创建人可编辑团队任务");
        }
        validateAssignment(request, existing.getCreatorId());
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
        return requireTask(taskId);
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
            return existing;
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
        return requireTask(taskId);
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
