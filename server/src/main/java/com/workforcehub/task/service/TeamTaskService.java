package com.workforcehub.task.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.dto.TeamTaskSaveRequest;
import com.workforcehub.task.repository.TeamTaskRepository;
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
        TeamTaskResponse task = new TeamTaskResponse();
        task.setTaskId(IdGenerator.nextId());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setAssigneeId(request.getAssigneeId());
        task.setCreatorId(CurrentUserHolder.requireUserId());
        task.setStatus("TODO");
        task.setStartDate(request.getStartDate());
        task.setEndDate(request.getEndDate());
        task.setGroupId(request.getGroupId());
        teamTaskRepository.insert(task);
        return teamTaskRepository.findById(task.getTaskId()).orElseThrow(() -> new BizException(500, "团队任务创建失败"));
    }

    public List<TeamTaskResponse> list(Long assigneeId, String status, java.time.LocalDate start, java.time.LocalDate end) {
        if (StringUtils.hasText(status) && !ALLOWED_STATUSES.contains(status)) {
            throw new BizException(400, "无效的任务状态");
        }
        return teamTaskRepository.find(assigneeId, status, start, end);
    }

    public TeamTaskResponse detail(Long taskId) {
        return requireTask(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse update(Long taskId, TeamTaskSaveRequest request) {
        TeamTaskResponse existing = requireTask(taskId);
        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setAssigneeId(request.getAssigneeId());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setGroupId(request.getGroupId());
        teamTaskRepository.update(existing);
        return requireTask(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamTaskResponse updateStatus(Long taskId, String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BizException(400, "无效的任务状态");
        }
        requireTask(taskId);
        teamTaskRepository.updateStatus(taskId, status);
        return requireTask(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long taskId) {
        requireTask(taskId);
        teamTaskRepository.deleteById(taskId);
    }

    private TeamTaskResponse requireTask(Long taskId) {
        return teamTaskRepository.findById(taskId)
            .orElseThrow(() -> new BizException(404, "团队任务不存在"));
    }
}
