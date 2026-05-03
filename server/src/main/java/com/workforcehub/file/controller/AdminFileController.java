package com.workforcehub.file.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.service.FileCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台文件中心治理接口。
 */
@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final FileCenterService fileCenterService;

    public AdminFileController(FileCenterService fileCenterService) {
        this.fileCenterService = fileCenterService;
    }

    @GetMapping("/storage-summary")
    public R<StorageSummaryResponse> storageSummary() {
        return R.ok(fileCenterService.getStorageSummary());
    }
}
