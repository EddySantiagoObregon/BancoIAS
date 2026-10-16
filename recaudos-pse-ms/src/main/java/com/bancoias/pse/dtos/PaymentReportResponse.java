package com.bancoias.pse.dtos;

import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentReportResponse(
        String paymentReference,
        String collectionOrderId,
        String merchantId,
        BigDecimal amount,
        PaymentStatus status,
        PaymentResultReason reasonCode,
        String reasonMessage,
        BigDecimal balanceAfter,
        OffsetDateTime processedAt) {
}
