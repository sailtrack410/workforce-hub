package com.workforcehub.personal.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.personal.dto.PersonalTodoResponse;
import com.workforcehub.personal.dto.PersonalTodoSaveRequest;
import com.workforcehub.personal.service.PersonalTodoService;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工端个人待办接口。
 */
// 个人待办：只看自己的，创建/更新/完成/重开/删除
@RestController
@ConditionalOnProperty(prefix = "workforce.personal", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/employee/personal-todos")
public class EmployeePersonalTodoController {

    private final PersonalTodoService personalTodoService;

    public EmployeePersonalTodoController(PersonalTodoService personalTodoService) {
        this.personalTodoService = personalTodoService;
    }

    @Operation(summary = "查询我的个人待办列表", operationId = "listPersonalTodos")
    @GetMapping
    public R<List<PersonalTodoResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return R.ok(personalTodoService.list(category, status, start, end));
    }

    @Operation(summary = "查询个人待办详情", operationId = "getPersonalTodo")
    @GetMapping("/{todoId}")
    public R<PersonalTodoResponse> detail(@PathVariable Long todoId) {
        return R.ok(personalTodoService.detail(todoId));
    }

    @Operation(summary = "创建个人待办", operationId = "createPersonalTodo")
    @PostMapping
    public R<PersonalTodoResponse> create(@Valid @RequestBody PersonalTodoSaveRequest request) {
        return R.ok(personalTodoService.create(request));
    }

    @Operation(summary = "更新个人待办", operationId = "updatePersonalTodo")
    @PutMapping("/{todoId}")
    public R<PersonalTodoResponse> update(@PathVariable Long todoId, @Valid @RequestBody PersonalTodoSaveRequest request) {
        return R.ok(personalTodoService.update(todoId, request));
    }

    @Operation(summary = "完成个人待办", operationId = "completePersonalTodo")
    @PutMapping("/{todoId}/complete")
    public R<PersonalTodoResponse> complete(@PathVariable Long todoId, @RequestParam(required = false) Integer expectedVersion) {
        return R.ok(personalTodoService.complete(todoId, expectedVersion));
    }

    @Operation(summary = "重开个人待办", operationId = "reopenPersonalTodo")
    @PutMapping("/{todoId}/reopen")
    public R<PersonalTodoResponse> reopen(@PathVariable Long todoId, @RequestParam(required = false) Integer expectedVersion) {
        return R.ok(personalTodoService.reopen(todoId, expectedVersion));
    }

    @Operation(summary = "删除个人待办", operationId = "deletePersonalTodo")
    @DeleteMapping("/{todoId}")
    public R<Void> delete(@PathVariable Long todoId, @RequestParam(required = false) Integer expectedVersion) {
        personalTodoService.delete(todoId, expectedVersion);
        return R.ok(null);
    }
}
