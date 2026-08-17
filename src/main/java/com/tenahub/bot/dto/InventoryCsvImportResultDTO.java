package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCsvImportResultDTO {
    private boolean success;
    private int rowCount;
    private int appliedCount;
    @Builder.Default
    private List<RowIssue> errors = new ArrayList<>();
    @Builder.Default
    private List<RowIssue> warnings = new ArrayList<>();
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowIssue {
        private int line;
        private String medicineName;
        private String field;
        private String message;
    }
}
