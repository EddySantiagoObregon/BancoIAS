package com.bancoias.pse.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;

/**
 * Base para pruebas contra un PostgreSQL real levantado con Testcontainers. Un único contenedor se
 * comparte entre clases; antes de cada prueba se dejan las órdenes en su estado semilla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresIntegrationTest {

    protected static final String ORDER_OPEN = "ORD-PSE-1001";
    protected static final String ORDER_BLOCKED = "ORD-PSE-1002";
    protected static final String ORDER_OTHER_MERCHANT = "ORD-PSE-2001";
    protected static final String MERCHANT_10 = "COM-10";
    protected static final String MERCHANT_20 = "COM-20";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bancoias_pse_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired
    protected DatabaseClient databaseClient;

    @BeforeEach
    void resetDatabase() {
        databaseClient.sql("DELETE FROM payment_reports").then()
                .then(databaseClient.sql("DELETE FROM collection_orders").then())
                .then(databaseClient.sql("""
                        INSERT INTO collection_orders (id, merchant_id, status, pending_balance) VALUES
                            ('ORD-PSE-1001', 'COM-10', 'OPEN',    1000000.00),
                            ('ORD-PSE-1002', 'COM-10', 'BLOCKED',  800000.00),
                            ('ORD-PSE-2001', 'COM-20', 'OPEN',    2000000.00)
                        """).then())
                .block();
    }

    protected BigDecimal balanceOf(String orderId) {
        return databaseClient.sql("SELECT pending_balance FROM collection_orders WHERE id = :id")
                .bind("id", orderId)
                .map(row -> row.get("pending_balance", BigDecimal.class))
                .one()
                .block();
    }

    protected long countReports(String paymentReference) {
        return databaseClient.sql("SELECT count(*) AS total FROM payment_reports WHERE payment_reference = :ref")
                .bind("ref", paymentReference)
                .map(row -> row.get("total", Long.class))
                .one()
                .block();
    }

    protected long countReportsByStatus(String orderId, String status) {
        return databaseClient.sql("""
                        SELECT count(*) AS total FROM payment_reports
                         WHERE collection_order_id = :order AND status = :status
                        """)
                .bind("order", orderId)
                .bind("status", status)
                .map(row -> row.get("total", Long.class))
                .one()
                .block();
    }

    protected static BigDecimal cop(long value) {
        return BigDecimal.valueOf(value);
    }
}
