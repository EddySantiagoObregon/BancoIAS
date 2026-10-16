package com.bancoias.pse.exceptions;

import com.bancoias.pse.enums.PaymentResultReason;
import lombok.Getter;

/**
 * Señal interna del flujo reactivo: el pago no cumple una regla de negocio.
 * Nunca sale por HTTP; el servicio la convierte en un reporte REJECTED persistido.
 */
@Getter
public class PaymentRejectedException extends RuntimeException {

    private final PaymentResultReason reason;

    public PaymentRejectedException(PaymentResultReason reason) {
        super(reason.getMessage(), null, false, false);
        this.reason = reason;
    }
}
