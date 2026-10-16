package com.bancoias.pse.validators;

import com.bancoias.pse.collections.CollectionOrder;
import com.bancoias.pse.collections.PaymentReport;
import com.bancoias.pse.enums.OrderStatus;
import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.exceptions.PaymentRejectedException;
import com.bancoias.pse.records.PaymentCommand;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reglas de RF02. La verificación de saldo de este validador es solo un filtro temprano para dar un
 * motivo claro; la garantía real contra sobregiros es el UPDATE condicional del repositorio.
 */
@Component
public class PaymentRulesValidator {

    public Mono<PaymentCommand> validateAmount(PaymentCommand command) {
        if (command.amount().signum() <= 0) {
            return reject(PaymentResultReason.INVALID_AMOUNT);
        }
        return Mono.just(command);
    }

    public Mono<CollectionOrder> validateAgainstOrder(PaymentCommand command, CollectionOrder order) {
        if (!order.getMerchantId().equals(command.merchantId())) {
            return reject(PaymentResultReason.MERCHANT_MISMATCH);
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            return reject(PaymentResultReason.ORDER_NOT_ENABLED);
        }
        if (command.amount().compareTo(order.getPendingBalance()) > 0) {
            return reject(PaymentResultReason.INSUFFICIENT_BALANCE);
        }
        return Mono.just(order);
    }

    /**
     * Una referencia repetida es "la misma" si coincide orden, comercio y monto (comparando el
     * BigDecimal por valor, 600000 == 600000.00).
     */
    public boolean isSameReport(PaymentReport original, PaymentCommand command) {
        return Objects.equals(original.getCollectionOrderId(), command.collectionOrderId())
                && Objects.equals(original.getMerchantId(), command.merchantId())
                && sameAmount(original.getAmount(), command.amount());
    }

    private boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private <T> Mono<T> reject(PaymentResultReason reason) {
        return Mono.error(new PaymentRejectedException(reason));
    }
}
