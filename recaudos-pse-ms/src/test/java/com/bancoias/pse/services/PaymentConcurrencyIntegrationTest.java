package com.bancoias.pse.services;

import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.enums.PaymentStatus;
import com.bancoias.pse.records.PaymentCommand;
import com.bancoias.pse.records.PaymentProcessingResult;
import com.bancoias.pse.services.definitions.PaymentBusinessService;
import com.bancoias.pse.support.PostgresIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las solicitudes se lanzan en paralelo (subscribeOn + merge) y cada una toma su propia conexión del
 * pool R2DBC, así que compiten de verdad por la misma fila en PostgreSQL.
 */
class PaymentConcurrencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private PaymentBusinessService paymentBusinessService;

    @RepeatedTest(5)
    void twoSimultaneousPaymentsCannotOverdrawTheOrder() {
        List<PaymentProcessingResult> results = runConcurrently(
                command("CONC-A", 700_000),
                command("CONC-B", 600_000));

        assertThat(results).hasSize(2);
        assertThat(results).filteredOn(r -> r.report().status() == PaymentStatus.APPLIED).hasSize(1);
        assertThat(results).filteredOn(r -> r.report().status() == PaymentStatus.REJECTED)
                .singleElement()
                .satisfies(r -> assertThat(r.report().reasonCode()).isEqualTo(PaymentResultReason.INSUFFICIENT_BALANCE));

        BigDecimal applied = results.stream()
                .filter(r -> r.report().status() == PaymentStatus.APPLIED)
                .map(r -> r.report().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo(cop(1_000_000).subtract(applied));
        assertThat(countReportsByStatus(ORDER_OPEN, "APPLIED")).isEqualTo(1);
        assertThat(countReportsByStatus(ORDER_OPEN, "REJECTED")).isEqualTo(1);
    }

    @Test
    void manySimultaneousPartialPaymentsApplyExactlyWhatTheBalanceAllows() {
        // 15 pagos de 150.000 sobre 1.000.000: caben 6 (900.000), el resto se rechaza.
        PaymentCommand[] commands = new PaymentCommand[15];
        for (int i = 0; i < commands.length; i++) {
            commands[i] = command("CONC-MANY-" + i, 150_000);
        }

        List<PaymentProcessingResult> results = runConcurrently(commands);

        assertThat(results).filteredOn(r -> r.report().status() == PaymentStatus.APPLIED).hasSize(6);
        assertThat(results).filteredOn(r -> r.report().status() == PaymentStatus.REJECTED).hasSize(9);
        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("100000");
        assertThat(countReportsByStatus(ORDER_OPEN, "APPLIED")).isEqualTo(6);
    }

    @RepeatedTest(5)
    void simultaneousRequestsWithSameReferenceApplyOnlyOnce() {
        PaymentCommand same = command("CONC-SAME-REF", 400_000);

        List<PaymentProcessingResult> results = runConcurrently(same, same, same, same);

        assertThat(results).hasSize(4);
        assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
        assertThat(results).filteredOn(PaymentProcessingResult::replayed).hasSize(3);
        assertThat(results).allSatisfy(r -> assertThat(r.report().status()).isEqualTo(PaymentStatus.APPLIED));
        assertThat(results).extracting(r -> r.report().processedAt()).containsOnly(results.get(0).report().processedAt());

        assertThat(countReports("CONC-SAME-REF")).isEqualTo(1);
        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("600000");
    }

    private List<PaymentProcessingResult> runConcurrently(PaymentCommand... commands) {
        List<Mono<PaymentProcessingResult>> calls = java.util.Arrays.stream(commands)
                .map(c -> paymentBusinessService.process(c).subscribeOn(Schedulers.parallel()))
                .toList();
        return Flux.merge(calls)
                .collectList()
                .block(Duration.ofSeconds(30));
    }

    private static PaymentCommand command(String reference, long amount) {
        return new PaymentCommand(reference, ORDER_OPEN, MERCHANT_10, cop(amount));
    }
}
