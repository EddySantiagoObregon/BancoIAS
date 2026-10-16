package com.bancoias.pse.repositories;

import com.bancoias.pse.collections.PaymentReport;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentReportRepository extends ReactiveCrudRepository<PaymentReport, Long> {

    Mono<PaymentReport> findByPaymentReference(String paymentReference);

    @Query("SELECT * FROM payment_reports ORDER BY processed_at DESC, id DESC LIMIT :limit")
    Flux<PaymentReport> findRecent(int limit);

    @Query("""
            SELECT * FROM payment_reports
             WHERE collection_order_id = :collectionOrderId
             ORDER BY processed_at DESC, id DESC
             LIMIT :limit
            """)
    Flux<PaymentReport> findRecentByOrder(String collectionOrderId, int limit);
}
