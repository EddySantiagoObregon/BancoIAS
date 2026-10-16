package com.bancoias.pse.services.impls;

import com.bancoias.pse.collections.PaymentReport;
import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.exceptions.PaymentReferenceConflictException;
import com.bancoias.pse.exceptions.PaymentRejectedException;
import com.bancoias.pse.mappers.PaymentReportMapper;
import com.bancoias.pse.records.PaymentCommand;
import com.bancoias.pse.records.PaymentProcessingResult;
import com.bancoias.pse.repositories.PaymentReportRepository;
import com.bancoias.pse.services.definitions.CollectionOrderService;
import com.bancoias.pse.services.definitions.PaymentBusinessService;
import com.bancoias.pse.validators.PaymentRulesValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBusinessServiceImpl implements PaymentBusinessService {

    private final PaymentReportRepository paymentReportRepository;
    private final CollectionOrderService collectionOrderService;
    private final PaymentRulesValidator paymentRulesValidator;
    private final PaymentReportMapper paymentReportMapper;
    private final TransactionalOperator transactionalOperator;
    private final Clock clock;

    @Override
    public Mono<PaymentProcessingResult> process(PaymentCommand command) {
        // El onErrorResume va por fuera de la transacción a propósito: primero se hace rollback
        // (incluido un posible descuento de saldo) y luego se resuelve el duplicado.
        return processInTransaction(command)
                .as(transactionalOperator::transactional)
                .onErrorResume(DataIntegrityViolationException.class, ex -> resolveInsertRace(command, ex));
    }

    private Mono<PaymentProcessingResult> processInTransaction(PaymentCommand command) {
        return paymentReportRepository.findByPaymentReference(command.paymentReference())
                .flatMap(existing -> resolveExisting(existing, command))
                .switchIfEmpty(Mono.defer(() -> processNewReport(command)));
    }

    private Mono<PaymentProcessingResult> processNewReport(PaymentCommand command) {
        return applyOrReject(command)
                .flatMap(paymentReportRepository::save)
                .doOnNext(saved -> log.info("Reporte {} procesado: {} ({})",
                        saved.getPaymentReference(), saved.getStatus(), saved.getReasonCode()))
                .map(paymentReportMapper::toResponse)
                .map(PaymentProcessingResult::created);
    }

    /**
     * Cada regla que falla emite PaymentRejectedException; el onErrorResume final la convierte en un
     * reporte REJECTED que igual se persiste. Si llega al final sin errores, el saldo ya fue descontado.
     */
    private Mono<PaymentReport> applyOrReject(PaymentCommand command) {
        return paymentRulesValidator.validateAmount(command)
                .flatMap(valid -> collectionOrderService.findById(valid.collectionOrderId()))
                .switchIfEmpty(Mono.error(() -> new PaymentRejectedException(PaymentResultReason.ORDER_NOT_FOUND)))
                .flatMap(order -> paymentRulesValidator.validateAgainstOrder(command, order))
                .flatMap(order -> collectionOrderService.debit(order.getId(), command.merchantId(), command.amount())
                        .switchIfEmpty(Mono.error(() ->
                                new PaymentRejectedException(PaymentResultReason.INSUFFICIENT_BALANCE))))
                .map(balanceAfter -> buildReport(command, PaymentResultReason.PAYMENT_APPLIED, balanceAfter))
                .onErrorResume(PaymentRejectedException.class,
                        ex -> Mono.fromSupplier(() -> buildReport(command, ex.getReason(), null)));
    }

    private Mono<PaymentProcessingResult> resolveExisting(PaymentReport existing, PaymentCommand command) {
        if (paymentRulesValidator.isSameReport(existing, command)) {
            log.info("Referencia {} repetida con los mismos datos, se devuelve el resultado original",
                    command.paymentReference());
            return Mono.just(PaymentProcessingResult.replayed(paymentReportMapper.toResponse(existing)));
        }
        log.warn("Referencia {} repetida con datos diferentes, se rechaza con conflicto", command.paymentReference());
        return Mono.error(new PaymentReferenceConflictException(command.paymentReference()));
    }

    /**
     * Dos solicitudes con la misma referencia pasaron la consulta inicial al mismo tiempo. La que
     * pierde choca con el UNIQUE, su transacción ya se revirtió y aquí se lee el reporte ganador.
     */
    private Mono<PaymentProcessingResult> resolveInsertRace(PaymentCommand command, DataIntegrityViolationException ex) {
        log.info("Carrera de inserción para la referencia {}, se resuelve contra el reporte existente",
                command.paymentReference());
        return paymentReportRepository.findByPaymentReference(command.paymentReference())
                .switchIfEmpty(Mono.error(ex))
                .flatMap(existing -> resolveExisting(existing, command));
    }

    private PaymentReport buildReport(PaymentCommand command, PaymentResultReason reason, BigDecimal balanceAfter) {
        OffsetDateTime processedAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        return paymentReportMapper.toReport(command, reason, balanceAfter, processedAt);
    }
}
