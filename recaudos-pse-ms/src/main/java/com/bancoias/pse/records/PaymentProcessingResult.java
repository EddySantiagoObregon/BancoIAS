package com.bancoias.pse.records;

import com.bancoias.pse.dtos.PaymentReportResponse;

/**
 * @param replayed true cuando la referencia ya existía y se devuelve el resultado original sin reprocesar.
 */
public record PaymentProcessingResult(PaymentReportResponse report, boolean replayed) {

    public static PaymentProcessingResult created(PaymentReportResponse report) {
        return new PaymentProcessingResult(report, false);
    }

    public static PaymentProcessingResult replayed(PaymentReportResponse report) {
        return new PaymentProcessingResult(report, true);
    }
}
