package com.workforcehub.system.dto;

import java.time.LocalDateTime;

/**
 * 数据字典条目响应
 */
public record DictItemResponse(
    Long id,
    String dictType,
    String itemCode,
    String itemLabel,
    Integer sortNo,
    Integer status,
    String statusName,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
