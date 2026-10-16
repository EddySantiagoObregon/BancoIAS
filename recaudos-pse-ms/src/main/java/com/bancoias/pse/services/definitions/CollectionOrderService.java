package com.bancoias.pse.services.definitions;

import com.bancoias.pse.collections.CollectionOrder;
import com.bancoias.pse.dtos.CollectionOrderResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface CollectionOrderService {

    Mono<CollectionOrder> findById(String orderId);

    /**
     * Descuenta el monto si la orden sigue abierta, pertenece al comercio y tiene saldo suficiente.
     * Emite el saldo resultante o termina vacío si la condición no se cumplió.
     */
    Mono<BigDecimal> debit(String orderId, String merchantId, BigDecimal amount);

    Flux<CollectionOrderResponse> findAll();
}
