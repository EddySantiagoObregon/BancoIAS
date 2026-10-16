package com.bancoias.pse.validators;

import com.bancoias.pse.collections.CollectionOrder;
import com.bancoias.pse.collections.PaymentReport;
import com.bancoias.pse.enums.OrderStatus;
import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.exceptions.PaymentRejectedException;
import com.bancoias.pse.records.PaymentCommand;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRulesValidatorTest {

    private final PaymentRulesValidator validator = new PaymentRulesValidator();

    private final CollectionOrder openOrder = CollectionOrder.builder()
            .id("ORD-PSE-1001").merchantId("COM-10").status(OrderStatus.OPEN)
            .pendingBalance(new BigDecimal("1000000.00")).build();

    @Test
    void acceptsPositiveAmount() {
        PaymentCommand command = command("ORD-PSE-1001", "COM-10", "1");
        StepVerifier.create(validator.validateAmount(command))
                .expectNext(command)
                .verifyComplete();
    }

    @Test
    void rejectsZeroAmount() {
        StepVerifier.create(validator.validateAmount(command("ORD-PSE-1001", "COM-10", "0")))
                .expectErrorMatches(rejectedWith(PaymentResultReason.INVALID_AMOUNT))
                .verify();
    }

    @Test
    void rejectsNegativeAmount() {
        StepVerifier.create(validator.validateAmount(command("ORD-PSE-1001", "COM-10", "-5000")))
                .expectErrorMatches(rejectedWith(PaymentResultReason.INVALID_AMOUNT))
                .verify();
    }

    @Test
    void rejectsMerchantThatDoesNotOwnTheOrder() {
        StepVerifier.create(validator.validateAgainstOrder(command("ORD-PSE-1001", "COM-20", "1000"), openOrder))
                .expectErrorMatches(rejectedWith(PaymentResultReason.MERCHANT_MISMATCH))
                .verify();
    }

    @Test
    void rejectsBlockedOrder() {
        CollectionOrder blocked = CollectionOrder.builder()
                .id("ORD-PSE-1002").merchantId("COM-10").status(OrderStatus.BLOCKED)
                .pendingBalance(new BigDecimal("800000")).build();

        StepVerifier.create(validator.validateAgainstOrder(command("ORD-PSE-1002", "COM-10", "1000"), blocked))
                .expectErrorMatches(rejectedWith(PaymentResultReason.ORDER_NOT_ENABLED))
                .verify();
    }

    @Test
    void rejectsAmountGreaterThanPendingBalance() {
        StepVerifier.create(validator.validateAgainstOrder(command("ORD-PSE-1001", "COM-10", "1000000.01"), openOrder))
                .expectErrorMatches(rejectedWith(PaymentResultReason.INSUFFICIENT_BALANCE))
                .verify();
    }

    @Test
    void acceptsAmountEqualToPendingBalance() {
        StepVerifier.create(validator.validateAgainstOrder(command("ORD-PSE-1001", "COM-10", "1000000"), openOrder))
                .expectNext(openOrder)
                .verifyComplete();
    }

    @Test
    void sameReportComparesAmountByValueNotByScale() {
        PaymentReport original = PaymentReport.builder()
                .paymentReference("PSE-REF-001").collectionOrderId("ORD-PSE-1001")
                .merchantId("COM-10").amount(new BigDecimal("600000.00")).build();

        assertThat(validator.isSameReport(original, command("ORD-PSE-1001", "COM-10", "600000"))).isTrue();
        assertThat(validator.isSameReport(original, command("ORD-PSE-1001", "COM-10", "600001"))).isFalse();
        assertThat(validator.isSameReport(original, command("ORD-PSE-2001", "COM-10", "600000"))).isFalse();
        assertThat(validator.isSameReport(original, command("ORD-PSE-1001", "COM-20", "600000"))).isFalse();
    }

    private static PaymentCommand command(String order, String merchant, String amount) {
        return new PaymentCommand("PSE-REF-001", order, merchant, new BigDecimal(amount));
    }

    private static java.util.function.Predicate<Throwable> rejectedWith(PaymentResultReason reason) {
        return ex -> ex instanceof PaymentRejectedException rejected && rejected.getReason() == reason;
    }
}
