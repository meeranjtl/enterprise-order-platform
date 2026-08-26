package com.enterprise.order.shared.outbox;

import com.enterprise.order.shared.dto.OutboxEventDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OutboxEventMapper {
    OutboxEventDTO toDto(OutboxEvent entity);
}
