package com.bancoias.pse.records;

import java.math.BigDecimal;

/**
 * Reporte de pago ya validado en formato, listo para pasar por las reglas de negocio.
 */
public record PaymentCommand(
        String paymentReference,
        String collectionOrderId,
        String merchantId,
        BigDecimal amount) {

    public PaymentCommand {
        paymentReference = trim(paymentReference);
        collectionOrderId = trim(collectionOrderId);
        merchantId = trim(merchantId);
        if (amount != null && amount.scale() < 2) {
            amount = amount.setScale(2);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
