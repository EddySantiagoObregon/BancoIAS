package com.bancoias.pse.services.impls;

import com.bancoias.pse.collections.CollectionOrder;
import com.bancoias.pse.dtos.CollectionOrderResponse;
import com.bancoias.pse.mappers.CollectionOrderMapper;
import com.bancoias.pse.repositories.CollectionOrderRepository;
import com.bancoias.pse.services.definitions.CollectionOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class CollectionOrderServiceImpl implements CollectionOrderService {

    private final CollectionOrderRepository collectionOrderRepository;
    private final CollectionOrderMapper collectionOrderMapper;

    @Override
    public Mono<CollectionOrder> findById(String orderId) {
        return collectionOrderRepository.findById(orderId);
    }

    @Override
    public Mono<BigDecimal> debit(String orderId, String merchantId, BigDecimal amount) {
        return collectionOrderRepository.debitIfAvailable(orderId, merchantId, amount);
    }

    @Override
    public Flux<CollectionOrderResponse> findAll() {
        return collectionOrderRepository.findAll()
                .sort(Comparator.comparing(CollectionOrder::getId))
                .map(collectionOrderMapper::toResponse);
    }
}
