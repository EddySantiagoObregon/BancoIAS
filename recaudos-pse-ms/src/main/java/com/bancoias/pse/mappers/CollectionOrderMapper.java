package com.bancoias.pse.mappers;

import com.bancoias.pse.collections.CollectionOrder;
import com.bancoias.pse.dtos.CollectionOrderResponse;
import org.mapstruct.Mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper
public interface CollectionOrderMapper {

    CollectionOrderResponse toResponse(CollectionOrder order);

    default OffsetDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
