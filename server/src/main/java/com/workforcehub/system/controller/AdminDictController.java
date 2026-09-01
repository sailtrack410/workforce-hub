package com.workforcehub.system.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.system.dto.DictItemResponse;
import com.workforcehub.system.dto.DictItemSaveRequest;
import com.workforcehub.system.service.AdminDictService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据字典管理接口
 */
@RestController
@RequestMapping("/api/admin/dicts")
public class AdminDictController {

    private final AdminDictService service;

    public AdminDictController(AdminDictService service) {
        this.service = service;
    }

    @Operation(summary = "查询字典类型列表", operationId = "listDictTypes")
    @GetMapping("/types")
    public R<List<Map<String, Object>>> listTypes() {
        return R.ok(service.listTypes());
    }

    @Operation(summary = "查询字典条目列表", operationId = "listDictItems")
    @GetMapping
    public R<List<DictItemResponse>> listItems(@RequestParam String dictType,
                                               @RequestParam(required = false) Integer status) {
        return R.ok(service.listByType(dictType, status));
    }

    @Operation(summary = "创建字典条目", operationId = "createDictItem")
    @PostMapping
    public R<DictItemResponse> create(@Valid @RequestBody DictItemSaveRequest request) {
        return R.ok(service.create(request));
    }

    @Operation(summary = "更新字典条目", operationId = "updateDictItem")
    @PutMapping("/{id}")
    public R<DictItemResponse> update(@PathVariable Long id,
                                      @Valid @RequestBody DictItemSaveRequest request) {
        return R.ok(service.update(id, request));
    }

    @Operation(summary = "删除字典条目", operationId = "deleteDictItem")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
