package com.hhplus.ecommerce.infrastructure.converter;

import com.hhplus.ecommerce.domain.vo.DiscountRate;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DiscountRateConverter implements AttributeConverter<DiscountRate, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DiscountRate attribute) {
        return attribute == null ? null : attribute.getPercentage();
    }

    @Override
    public DiscountRate convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new DiscountRate(dbData);
    }
}
