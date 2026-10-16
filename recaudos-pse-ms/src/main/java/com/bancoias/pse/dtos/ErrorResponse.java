package com.bancoias.pse.dtos;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> details) {

    public record FieldErrorDetail(String field, String message) {
    }
}
