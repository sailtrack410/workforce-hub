package com.workforcehub.org.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点
 */
@Data
public class OrgDepartmentTreeNodeResponse {

    private Long id;
    private Long parentId;
    private String treePath;
    private String deptCode;
    private String deptName;
    private String deptType;
    private String deptTypeName;
    private Integer deptLevel;
    private Long leaderUserId;
    private String leaderName;
    private String status;
    private String statusName;
    private List<OrgDepartmentTreeNodeResponse> children = new ArrayList<>();
}
