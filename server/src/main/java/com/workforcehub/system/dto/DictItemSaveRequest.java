package com.workforcehub.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 数据字典条目保存请求
 */
public record DictItemSaveRequest(
    @NotBlank(message = "字典类型不能为空") @Size(max = 64, message = "字典类型长度不能超过64") String dictType,
    @NotBlank(message = "条目编码不能为空") @Size(max = 64, message = "条目编码长度不能超过64") String itemCode,
    @NotBlank(message = "条目名称不能为空") @Size(max = 128, message = "条目名称长度不能超过128") String itemLabel,
    @Min(value = 0, message = "排序号不能为负数") Integer sortNo,
    Integer status,
    @Size(max = 256, message = "备注长度不能超过256") String remark
) {}
