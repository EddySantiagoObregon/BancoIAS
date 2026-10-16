package com.bancoias.pse.exceptions;

import org.springframework.http.HttpStatus;

public class PaymentReferenceConflictException extends ApiException {

    public PaymentReferenceConflictException(String paymentReference) {
        super(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_CONFLICT",
                "La referencia " + paymentReference
                        + " ya fue procesada con datos diferentes; se conserva el reporte original");
    }
}
