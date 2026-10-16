package com.bancoias.pse.repositories;

import com.bancoias.pse.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el UPDATE condicional sin pasar por el validador, para dejar claro que la protección contra
 * sobregiros la da la base de datos y no la verificación previa en Java.
 */
class CollectionOrderRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CollectionOrderRepository collectionOrderRepository;

    @Test
    void debitReturnsNewBalanceWhenFundsAreAvailable() {
        StepVerifier.create(collectionOrderRepository.debitIfAvailable(ORDER_OPEN, MERCHANT_10, cop(250_000)))
                .assertNext(balance -> assertThat(balance).isEqualByComparingTo("750000"))
                .verifyComplete();
    }

    @Test
    void debitIsEmptyWhenAmountExceedsBalanceOrOrderIsBlockedOrMerchantDiffers() {
        StepVerifier.create(collectionOrderRepository.debitIfAvailable(ORDER_OPEN, MERCHANT_10, cop(1_000_001)))
                .verifyComplete();
        StepVerifier.create(collectionOrderRepository.debitIfAvailable(ORDER_BLOCKED, MERCHANT_10, cop(1)))
                .verifyComplete();
        StepVerifier.create(collectionOrderRepository.debitIfAvailable(ORDER_OPEN, MERCHANT_20, cop(1)))
                .verifyComplete();

        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("1000000");
        assertThat(balanceOf(ORDER_BLOCKED)).isEqualByComparingTo("800000");
    }

    @Test
    void concurrentDebitsNeverOverdraw() {
        // 20 descuentos simultáneos de 70.000 sobre 1.000.000: solo caben 14.
        Long applied = Flux.range(0, 20)
                .flatMap(i -> collectionOrderRepository.debitIfAvailable(ORDER_OPEN, MERCHANT_10, cop(70_000))
                        .subscribeOn(Schedulers.parallel()))
                .count()
                .block();

        assertThat(applied).isEqualTo(14);
        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo(new BigDecimal("20000"));
    }
}
