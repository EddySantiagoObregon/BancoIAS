package com.bancoias.pse.exceptions;

import org.springframework.http.HttpStatus;

public class PaymentReportNotFoundException extends ApiException {

    public PaymentReportNotFoundException(String paymentReference) {
        super(HttpStatus.NOT_FOUND, "PAYMENT_REPORT_NOT_FOUND",
                "No existe un reporte de pago con referencia " + paymentReference);
    }
}
