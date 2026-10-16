package com.bancoias.pse.collections;

import com.bancoias.pse.enums.OrderStatus;
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
@Table("collection_orders")
public class CollectionOrder {

    @Id
    private String id;

    @Column("merchant_id")
    private String merchantId;

    private OrderStatus status;

    @Column("pending_balance")
    private BigDecimal pendingBalance;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
