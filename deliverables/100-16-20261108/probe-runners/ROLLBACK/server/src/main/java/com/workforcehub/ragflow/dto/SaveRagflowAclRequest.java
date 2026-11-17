package com.workforcehub.ragflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SaveRagflowAclRequest {
    @Valid
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull
        private String subjectType;

        @NotNull
        private Long subjectId;

        private String permCode;
    }
}
