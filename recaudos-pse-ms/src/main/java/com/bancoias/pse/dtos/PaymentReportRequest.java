package com.bancoias.pse.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * El signo del monto NO se valida aquí: un monto cero o negativo es un rechazo de negocio (RF02)
 * y debe quedar persistido, no es un error de formato.
 */
public record PaymentReportRequest(

        @Schema(example = "PSE-REF-001")
        @NotBlank(message = "La referencia de pago es obligatoria")
        @Size(max = 50, message = "La referencia de pago admite máximo 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "La referencia solo admite letras, números, guion y guion bajo")
        String paymentReference,

        @Schema(example = "ORD-PSE-1001")
        @NotBlank(message = "La orden de recaudo es obligatoria")
        @Size(max = 30, message = "La orden de recaudo admite máximo 30 caracteres")
        String collectionOrderId,

        @Schema(example = "COM-10")
        @NotBlank(message = "El comercio es obligatorio")
        @Size(max = 30, message = "El comercio admite máximo 30 caracteres")
        String merchantId,

        @Schema(example = "600000")
        @NotNull(message = "El monto es obligatorio")
        @Digits(integer = 17, fraction = 2, message = "El monto admite máximo 17 enteros y 2 decimales")
        BigDecimal amount) {
}
