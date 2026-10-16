package com.bancoias.pse.services.definitions;

import com.bancoias.pse.records.PaymentCommand;
import com.bancoias.pse.records.PaymentProcessingResult;
import reactor.core.publisher.Mono;

public interface PaymentBusinessService {

    /**
     * Procesa un reporte PSE: lo aplica o lo rechaza y deja persistido el resultado.
     * Si la referencia ya existe con los mismos datos devuelve el resultado original (replayed);
     * si existe con datos distintos termina con PaymentReferenceConflictException.
     */
    Mono<PaymentProcessingResult> process(PaymentCommand command);
}
