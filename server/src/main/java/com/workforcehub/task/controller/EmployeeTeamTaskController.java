package com.workforcehub.task.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.dto.TeamTaskSaveRequest;
import com.workforcehub.task.dto.TeamTaskStatusRequest;
import com.workforcehub.task.service.TeamTaskService;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工端团队任务接口。
 */
// 团队任务：泳道图/列表/详情/创建/更新/改状态/删除
@RestController
@ConditionalOnProperty(prefix = "workforce.task", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/employee/team-tasks")
public class EmployeeTeamTaskController {

    private final TeamTaskService teamTaskService;

    public EmployeeTeamTaskController(TeamTaskService teamTaskService) {
        this.teamTaskService = teamTaskService;
    }

    @Operation(summary = "查询团队任务列表", operationId = "listTeamTasks")
    @GetMapping
    public R<List<TeamTaskResponse>> list(
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "ALL") String scope) {
        return R.ok(teamTaskService.list(assigneeId, status, start, end, scope));
    }

    @Operation(summary = "查询可派发任务的员工", operationId = "listTeamTaskAssignees")
    @GetMapping("/assignees")
    public R<List<OrgUserSummaryResponse>> assignees() {
        return R.ok(teamTaskService.assignees());
    }

    @Operation(summary = "创建团队任务", operationId = "createTeamTask")
    @PostMapping
    public R<TeamTaskResponse> create(@Valid @RequestBody TeamTaskSaveRequest request) {
        return R.ok(teamTaskService.create(request));
    }

    @Operation(summary = "查询团队任务详情", operationId = "getTeamTaskDetail")
    @GetMapping("/{taskId}")
    public R<TeamTaskResponse> detail(@PathVariable Long taskId) {
        return R.ok(teamTaskService.detail(taskId));
    }

    @Operation(summary = "更新团队任务", operationId = "updateTeamTask")
    @PutMapping("/{taskId}")
    public R<TeamTaskResponse> update(@PathVariable Long taskId, @Valid @RequestBody TeamTaskSaveRequest request) {
        return R.ok(teamTaskService.update(taskId, request));
    }

    @Operation(summary = "仅更新团队任务状态", operationId = "updateTeamTaskStatus")
    @PutMapping("/{taskId}/status")
    public R<TeamTaskResponse> updateStatus(@PathVariable Long taskId, @Valid @RequestBody TeamTaskStatusRequest request) {
        return R.ok(teamTaskService.updateStatus(taskId, request.getStatus()));
    }

    @Operation(summary = "删除团队任务", operationId = "deleteTeamTask")
    @DeleteMapping("/{taskId}")
    public R<Void> delete(@PathVariable Long taskId) {
        teamTaskService.delete(taskId);
        return R.ok(null);
    }
}
