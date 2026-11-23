package com.workforcehub.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamTaskTextRequest(@NotBlank(message = "内容不能为空")
                                  @Size(max = 2000, message = "内容不能超过2000个字符") String content) {}
