package com.bancoias.pse.dtos;

import com.bancoias.pse.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CollectionOrderResponse(
        String id,
        String merchantId,
        OrderStatus status,
        BigDecimal pendingBalance,
        OffsetDateTime updatedAt) {
}
