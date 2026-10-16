package com.bancoias.pse.services.impls;

import com.bancoias.pse.collections.PaymentReport;
import com.bancoias.pse.dtos.PaymentReportResponse;
import com.bancoias.pse.exceptions.PaymentReportNotFoundException;
import com.bancoias.pse.mappers.PaymentReportMapper;
import com.bancoias.pse.repositories.PaymentReportRepository;
import com.bancoias.pse.services.definitions.PaymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentReportRepository paymentReportRepository;
    private final PaymentReportMapper paymentReportMapper;

    @Override
    public Mono<PaymentReportResponse> findByReference(String paymentReference) {
        return paymentReportRepository.findByPaymentReference(paymentReference.trim())
                .map(paymentReportMapper::toResponse)
                .switchIfEmpty(Mono.error(() -> new PaymentReportNotFoundException(paymentReference)));
    }

    @Override
    public Flux<PaymentReportResponse> findRecent(String collectionOrderId, int limit) {
        Flux<PaymentReport> reports = (collectionOrderId == null || collectionOrderId.isBlank())
                ? paymentReportRepository.findRecent(limit)
                : paymentReportRepository.findRecentByOrder(collectionOrderId.trim(), limit);
        return reports.map(paymentReportMapper::toResponse);
    }
}
