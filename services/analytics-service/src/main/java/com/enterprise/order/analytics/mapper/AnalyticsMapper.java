package com.enterprise.order.analytics.mapper;

import com.enterprise.order.analytics.dto.DailyMetricDTO;
import com.enterprise.order.analytics.entity.DailyMetric;
import org.mapstruct.Mapper;

/**
 * Phase 9 gotcha: use the literal {@code componentModel = "spring"} — the
 * MappingConstants enum variant silently broke mapper bean registration in
 * this repo's Lombok/MapStruct setup.
 */
@Mapper(componentModel = "spring")
public interface AnalyticsMapper {

    DailyMetricDTO toDTO(DailyMetric entity);
}
