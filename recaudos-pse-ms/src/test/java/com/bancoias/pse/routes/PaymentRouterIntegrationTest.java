package com.bancoias.pse.routes;

import com.bancoias.pse.constants.ApiConstants;
import com.bancoias.pse.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class PaymentRouterIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void postAppliedPaymentReturns201WithLocation() {
        post(body("HTTP-REF-1", ORDER_OPEN, MERCHANT_10, 600000))
                .expectStatus().isCreated()
                .expectHeader().location(ApiConstants.PAYMENTS + "/HTTP-REF-1")
                .expectBody()
                .jsonPath("$.paymentReference").isEqualTo("HTTP-REF-1")
                .jsonPath("$.status").isEqualTo("APPLIED")
                .jsonPath("$.reasonCode").isEqualTo("PAYMENT_APPLIED")
                .jsonPath("$.balanceAfter").isEqualTo(400000.00)
                .jsonPath("$.processedAt").exists();
    }

    @Test
    void postRejectedPaymentReturns201WithReason() {
        post(body("HTTP-REF-BLOCK", ORDER_BLOCKED, MERCHANT_10, 1000))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.reasonCode").isEqualTo("ORDER_NOT_ENABLED")
                .jsonPath("$.reasonMessage").isNotEmpty()
                .jsonPath("$.balanceAfter").doesNotExist();
    }

    @Test
    void repeatedReferenceWithSameDataReturns200AndReplayHeader() {
        post(body("HTTP-DUP", ORDER_OPEN, MERCHANT_10, 100000)).expectStatus().isCreated();

        post(body("HTTP-DUP", ORDER_OPEN, MERCHANT_10, 100000))
                .expectStatus().isOk()
                .expectHeader().valueEquals(ApiConstants.HEADER_IDEMPOTENT_REPLAY, "true")
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPLIED")
                .jsonPath("$.balanceAfter").isEqualTo(900000.00);

        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("900000");
    }

    @Test
    void repeatedReferenceWithDifferentDataReturns409() {
        post(body("HTTP-CONFLICT", ORDER_OPEN, MERCHANT_10, 100000)).expectStatus().isCreated();

        post(body("HTTP-CONFLICT", ORDER_OPEN, MERCHANT_10, 250000))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PAYMENT_REFERENCE_CONFLICT");

        assertThat(balanceOf(ORDER_OPEN)).isEqualByComparingTo("900000");
    }

    @Test
    void malformedRequestReturns400AndIsNotPersisted() {
        webTestClient.post().uri(ApiConstants.PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentReference", "", "collectionOrderId", ORDER_OPEN, "merchantId", MERCHANT_10))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.details[?(@.field == 'amount')]").exists()
                .jsonPath("$.details[?(@.field == 'paymentReference')]").exists();

        assertThat(countReports("")).isZero();
    }

    @Test
    void nonNumericAmountReturns400() {
        webTestClient.post().uri(ApiConstants.PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"paymentReference\":\"HTTP-BAD\",\"collectionOrderId\":\"ORD-PSE-1001\",\"merchantId\":\"COM-10\",\"amount\":\"mil\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MALFORMED_REQUEST");

        assertThat(countReports("HTTP-BAD")).isZero();
    }

    @Test
    void getByReferenceReturnsStoredReport() {
        post(body("HTTP-GET", ORDER_OTHER_MERCHANT, MERCHANT_20, 50000)).expectStatus().isCreated();

        webTestClient.get().uri(ApiConstants.PAYMENTS + "/HTTP-GET")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paymentReference").isEqualTo("HTTP-GET")
                .jsonPath("$.collectionOrderId").isEqualTo(ORDER_OTHER_MERCHANT)
                .jsonPath("$.amount").isEqualTo(50000.00)
                .jsonPath("$.status").isEqualTo("APPLIED");
    }

    @Test
    void getUnknownReferenceReturns404() {
        webTestClient.get().uri(ApiConstants.PAYMENTS + "/NO-EXISTE")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PAYMENT_REPORT_NOT_FOUND");
    }

    @Test
    void recentReportsAreReturnedNewestFirstAndRespectLimit() {
        post(body("HTTP-RECENT-1", ORDER_OPEN, MERCHANT_10, 1000)).expectStatus().isCreated();
        post(body("HTTP-RECENT-2", ORDER_BLOCKED, MERCHANT_10, 1000)).expectStatus().isCreated();
        post(body("HTTP-RECENT-3", ORDER_OPEN, MERCHANT_10, 2000)).expectStatus().isCreated();

        webTestClient.get().uri(ApiConstants.PAYMENTS + "?limit=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].paymentReference").isEqualTo("HTTP-RECENT-3")
                .jsonPath("$[1].paymentReference").isEqualTo("HTTP-RECENT-2")
                .jsonPath("$[1].status").isEqualTo("REJECTED");
    }

    @Test
    void recentReportsCanBeFilteredByCollectionOrder() {
        post(body("HTTP-ORD-1", ORDER_OPEN, MERCHANT_10, 1000)).expectStatus().isCreated();
        post(body("HTTP-ORD-2", ORDER_OTHER_MERCHANT, MERCHANT_20, 1000)).expectStatus().isCreated();
        post(body("HTTP-ORD-3", ORDER_OPEN, MERCHANT_10, 2000)).expectStatus().isCreated();

        webTestClient.get().uri(ApiConstants.PAYMENTS + "?collectionOrderId=" + ORDER_OPEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].paymentReference").isEqualTo("HTTP-ORD-3")
                .jsonPath("$[1].paymentReference").isEqualTo("HTTP-ORD-1");
    }

    @Test
    void invalidLimitReturns400() {
        webTestClient.get().uri(ApiConstants.PAYMENTS + "?limit=abc")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void collectionOrdersExposeCurrentBalance() {
        post(body("HTTP-ORD", ORDER_OPEN, MERCHANT_10, 250000)).expectStatus().isCreated();

        webTestClient.get().uri(ApiConstants.COLLECTION_ORDERS)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].id").isEqualTo(ORDER_OPEN)
                .jsonPath("$[0].pendingBalance").isEqualTo(750000.00);
    }

    private WebTestClient.ResponseSpec post(Map<String, Object> body) {
        return webTestClient.post().uri(ApiConstants.PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private static Map<String, Object> body(String reference, String order, String merchant, long amount) {
        return Map.of("paymentReference", reference, "collectionOrderId", order,
                "merchantId", merchant, "amount", amount);
    }
}
