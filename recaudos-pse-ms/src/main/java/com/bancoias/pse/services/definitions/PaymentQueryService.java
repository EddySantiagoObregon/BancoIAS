package com.bancoias.pse.services.definitions;

import com.bancoias.pse.dtos.PaymentReportResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentQueryService {

    Mono<PaymentReportResponse> findByReference(String paymentReference);

    /**
     * @param collectionOrderId opcional; si viene vacío se devuelven los recientes de todas las órdenes.
     */
    Flux<PaymentReportResponse> findRecent(String collectionOrderId, int limit);
}
