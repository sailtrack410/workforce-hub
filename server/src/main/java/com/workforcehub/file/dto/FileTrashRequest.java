package com.workforcehub.file.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileTrashRequest {

    @Size(max = 256, message = "原因不能超过256个字符")
    private String reason;
}
