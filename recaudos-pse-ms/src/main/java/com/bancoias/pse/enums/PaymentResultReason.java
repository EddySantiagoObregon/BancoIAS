package com.bancoias.pse.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentResultReason {

    PAYMENT_APPLIED(PaymentStatus.APPLIED, "Pago aplicado a la orden de recaudo"),
    INVALID_AMOUNT(PaymentStatus.REJECTED, "El valor del pago debe ser mayor que cero"),
    ORDER_NOT_FOUND(PaymentStatus.REJECTED, "La orden de recaudo no existe"),
    MERCHANT_MISMATCH(PaymentStatus.REJECTED, "La orden de recaudo no pertenece al comercio informado"),
    ORDER_NOT_ENABLED(PaymentStatus.REJECTED, "La orden de recaudo no se encuentra habilitada"),
    INSUFFICIENT_BALANCE(PaymentStatus.REJECTED, "El valor reportado supera el saldo pendiente de la orden");

    private final PaymentStatus status;
    private final String message;
}
