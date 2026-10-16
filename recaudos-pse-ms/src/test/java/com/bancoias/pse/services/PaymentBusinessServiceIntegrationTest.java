package com.bancoias.pse.services;

import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.enums.PaymentStatus;
import com.bancoias.pse.exceptions.PaymentReferenceConflictException;
import com.bancoias.pse.records.PaymentCommand;
import com.bancoias.pse.services.definitions.PaymentBusinessService;
import com.bancoias.pse.services.definitions.PaymentQueryService;
import com.bancoias.pse.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentBusinessServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private PaymentBusinessService paymentBusinessService;

    @Autowired
    private PaymentQueryService paymentQueryService;

    @Test
    void appliesValidPaymentAndDebitsBalance() {
        StepVerifier.create(paymentBusinessService.process(command("REF-OK-1", ORDER_OPEN, MERCHANT_10, 600_000)))
                .assertNext(result -> {
                    assertThat(result.replayed()).isFalse();
                    assertThat(result.report().status()).isEqualTo(PaymentStatus.APPLIED);
                    assertThat(result.report().reasonCode()).isEqualTo(PaymentResultReason.PAYMENT_APPLIED);
                    assertThat(result.report().balanceAfter()).isEqualByComparingTo("400000");
                    assertThat(result.report().processedAt()).isNotNull();
                })
                .verifyComplete();

        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("400000");
    }

    @Test
    void allowsPartialPaymentsUntilBalanceIsExhausted() {
        StepVerifier.create(paymentBusinessService.process(command("REF-PART-1", ORDER_OPEN, MERCHANT_10, 300_000))
                        .then(paymentBusinessService.process(command("REF-PART-2", ORDER_OPEN, MERCHANT_10, 700_000))))
                .assertNext(result -> {
                    assertThat(result.report().status()).isEqualTo(PaymentStatus.APPLIED);
                    assertThat(result.report().balanceAfter()).isEqualByComparingTo("0");
                })
                .verifyComplete();

        StepVerifier.create(paymentBusinessService.process(command("REF-PART-3", ORDER_OPEN, MERCHANT_10, 1)))
                .assertNext(result -> assertThat(result.report().reasonCode())
                        .isEqualTo(PaymentResultReason.INSUFFICIENT_BALANCE))
                .verifyComplete();

        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("0");
    }

    @Test
    void rejectsZeroAmountAndPersistsTheReport() {
        assertRejectedWithoutDebit(command("REF-ZERO", ORDER_OPEN, MERCHANT_10, 0), PaymentResultReason.INVALID_AMOUNT);
    }

    @Test
    void rejectsNegativeAmountAndPersistsTheReport() {
        assertRejectedWithoutDebit(command("REF-NEG", ORDER_OPEN, MERCHANT_10, -1_000), PaymentResultReason.INVALID_AMOUNT);
    }

    @Test
    void rejectsUnknownOrder() {
        StepVerifier.create(paymentBusinessService.process(command("REF-NOORDER", "ORD-NO-EXISTE", MERCHANT_10, 1_000)))
                .assertNext(result -> {
                    assertThat(result.report().status()).isEqualTo(PaymentStatus.REJECTED);
                    assertThat(result.report().reasonCode()).isEqualTo(PaymentResultReason.ORDER_NOT_FOUND);
                })
                .verifyComplete();
        assertThat(countReports("REF-NOORDER")).isEqualTo(1);
    }

    @Test
    void rejectsBlockedOrder() {
        assertRejectedWithoutDebit(command("REF-BLOCKED", ORDER_BLOCKED, MERCHANT_10, 1_000),
                PaymentResultReason.ORDER_NOT_ENABLED);
    }

    @Test
    void rejectsMerchantThatDoesNotOwnTheOrder() {
        assertRejectedWithoutDebit(command("REF-MERCHANT", ORDER_OPEN, MERCHANT_20, 1_000),
                PaymentResultReason.MERCHANT_MISMATCH);
    }

    @Test
    void rejectsAmountAboveBalance() {
        assertRejectedWithoutDebit(command("REF-OVER", ORDER_OPEN, MERCHANT_10, 1_000_001),
                PaymentResultReason.INSUFFICIENT_BALANCE);
    }

    @Test
    void rejectedReportIsQueryableAndKeepsItsReason() {
        paymentBusinessService.process(command("REF-REJ-Q", ORDER_BLOCKED, MERCHANT_10, 5_000)).block();

        StepVerifier.create(paymentQueryService.findByReference("REF-REJ-Q"))
                .assertNext(report -> {
                    assertThat(report.status()).isEqualTo(PaymentStatus.REJECTED);
                    assertThat(report.reasonCode()).isEqualTo(PaymentResultReason.ORDER_NOT_ENABLED);
                    assertThat(report.reasonMessage()).isNotBlank();
                    assertThat(report.balanceAfter()).isNull();
                    assertThat(report.amount()).isEqualByComparingTo("5000");
                })
                .verifyComplete();
        assertThat(balanceOf(ORDER_BLOCKED)).isEqualByComparingTo("800000");
    }

    @Test
    void repeatedReferenceWithSameDataReturnsOriginalWithoutDebitingAgain() {
        PaymentCommand original = command("REF-DUP", ORDER_OPEN, MERCHANT_10, 200_000);
        var first = paymentBusinessService.process(original).block();

        StepVerifier.create(paymentBusinessService.process(
                        new PaymentCommand("REF-DUP", ORDER_OPEN, MERCHANT_10, new BigDecimal("200000.00"))))
                .assertNext(result -> {
                    assertThat(result.replayed()).isTrue();
                    assertThat(result.report()).isEqualTo(first.report());
                })
                .verifyComplete();

        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("800000");
        assertThat(countReports("REF-DUP")).isEqualTo(1);
    }

    @Test
    void repeatedRejectedReferenceKeepsTheOriginalRejection() {
        paymentBusinessService.process(command("REF-DUP-REJ", ORDER_BLOCKED, MERCHANT_10, 1_000)).block();

        StepVerifier.create(paymentBusinessService.process(command("REF-DUP-REJ", ORDER_BLOCKED, MERCHANT_10, 1_000)))
                .assertNext(result -> {
                    assertThat(result.replayed()).isTrue();
                    assertThat(result.report().reasonCode()).isEqualTo(PaymentResultReason.ORDER_NOT_ENABLED);
                })
                .verifyComplete();
        assertThat(countReports("REF-DUP-REJ")).isEqualTo(1);
    }

    @Test
    void repeatedReferenceWithDifferentDataIsAConflictAndPreservesOriginal() {
        paymentBusinessService.process(command("REF-CONFLICT", ORDER_OPEN, MERCHANT_10, 100_000)).block();

        StepVerifier.create(paymentBusinessService.process(command("REF-CONFLICT", ORDER_OPEN, MERCHANT_10, 900_000)))
                .expectError(PaymentReferenceConflictException.class)
                .verify();

        StepVerifier.create(paymentQueryService.findByReference("REF-CONFLICT"))
                .assertNext(report -> assertThat(report.amount()).isEqualByComparingTo("100000"))
                .verifyComplete();
        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("900000");
        assertThat(countReports("REF-CONFLICT")).isEqualTo(1);
    }

    private void assertRejectedWithoutDebit(PaymentCommand command, PaymentResultReason expectedReason) {
        BigDecimal before = balanceOf(command.collectionOrderId());

        StepVerifier.create(paymentBusinessService.process(command))
                .assertNext(result -> {
                    assertThat(result.report().status()).isEqualTo(PaymentStatus.REJECTED);
                    assertThat(result.report().reasonCode()).isEqualTo(expectedReason);
                    assertThat(result.report().balanceAfter()).isNull();
                })
                .verifyComplete();

        assertThat(balanceOf(command.collectionOrderId())).isEqualByComparingTo(before);
        assertThat(countReports(command.paymentReference())).isEqualTo(1);
    }

    private static PaymentCommand command(String reference, String order, String merchant, long amount) {
        return new PaymentCommand(reference, order, merchant, cop(amount));
    }
}
