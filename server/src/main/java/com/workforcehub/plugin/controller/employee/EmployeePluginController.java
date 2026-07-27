package com.workforcehub.plugin.controller.employee;

import com.workforcehub.common.response.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端插件清单接口。
 */
@RestController
@RequestMapping("/api/employee/plugins")
public class EmployeePluginController {

    private final EmployeePluginService employeePluginService;

    public EmployeePluginController(EmployeePluginService employeePluginService) {
        this.employeePluginService = employeePluginService;
    }

    @GetMapping("/mine")
    public R<List<EmployeePluginResponse>> mine() {
        return R.ok(employeePluginService.listVisiblePlugins());
    }
}
