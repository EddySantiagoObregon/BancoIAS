package com.bancoias.pse.mappers;

import com.bancoias.pse.collections.PaymentReport;
import com.bancoias.pse.dtos.PaymentReportRequest;
import com.bancoias.pse.dtos.PaymentReportResponse;
import com.bancoias.pse.enums.PaymentResultReason;
import com.bancoias.pse.records.PaymentCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper
public interface PaymentReportMapper {

    PaymentCommand toCommand(PaymentReportRequest request);

    PaymentReportResponse toResponse(PaymentReport report);

    /**
     * PostgreSQL devuelve el timestamptz con el offset de la sesión; se normaliza a UTC para que el
     * resultado recién procesado y el consultado después sean idénticos.
     */
    default OffsetDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(reason.getStatus())")
    @Mapping(target = "reasonCode", source = "reason")
    @Mapping(target = "reasonMessage", expression = "java(reason.getMessage())")
    PaymentReport toReport(PaymentCommand command,
                           PaymentResultReason reason,
                           BigDecimal balanceAfter,
                           OffsetDateTime processedAt);
}
