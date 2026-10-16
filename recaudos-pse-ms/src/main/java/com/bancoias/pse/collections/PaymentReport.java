package com.bancoias.pse.collections;

import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_reports")
public class PaymentReport {

    @Id
    private Long id;

    @Column("payment_reference")
    private String paymentReference;

    @Column("collection_order_id")
    private String collectionOrderId;

    @Column("merchant_id")
    private String merchantId;

    private BigDecimal amount;

    private PaymentStatus status;

    @Column("reason_code")
    private PaymentResultReason reasonCode;

    @Column("reason_message")
    private String reasonMessage;

    /** Saldo de la orden justo después de aplicar el pago; null si fue rechazado. */
    @Column("balance_after")
    private BigDecimal balanceAfter;

    @Column("processed_at")
    private OffsetDateTime processedAt;
}
