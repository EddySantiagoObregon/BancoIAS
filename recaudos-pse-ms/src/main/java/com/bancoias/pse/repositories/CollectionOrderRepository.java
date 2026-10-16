package com.bancoias.pse.repositories;

import com.bancoias.pse.collections.CollectionOrder;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface CollectionOrderRepository extends ReactiveCrudRepository<CollectionOrder, String> {

    /**
     * Descuento atómico: la condición y la resta se evalúan en la misma sentencia, con el row lock
     * de PostgreSQL. Si otra transacción ya consumió el saldo, no se actualiza ninguna fila y el
     * Mono termina vacío.
     */
    @Query("""
            UPDATE collection_orders
               SET pending_balance = pending_balance - :amount,
                   updated_at = now()
             WHERE id = :orderId
               AND merchant_id = :merchantId
               AND status = 'OPEN'
               AND pending_balance >= :amount
            RETURNING pending_balance
            """)
    Mono<BigDecimal> debitIfAvailable(String orderId, String merchantId, BigDecimal amount);
}
