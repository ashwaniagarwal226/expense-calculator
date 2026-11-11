package com.expense.calculator.dto;

/** Response of a statement upload. {@code message}/{@code status}/{@code fileName} keep the old UploadResponse shape. */
public record ImportResult(
        String message,
        String fileName,
        String status,
        String bank,
        boolean bankAutoDetected,
        int parsed,
        int inserted,
        int updated,
        int ruleCategorized,
        int aiCategorized,
        int uncategorized,
        boolean aiEnabled,
        String aiError,
        Long profileId) {
}
