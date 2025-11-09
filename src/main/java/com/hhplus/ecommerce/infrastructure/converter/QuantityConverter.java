package com.hhplus.ecommerce.infrastructure.converter;

import com.hhplus.ecommerce.domain.vo.Quantity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QuantityConverter implements AttributeConverter<Quantity, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Quantity attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Quantity convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new Quantity(dbData);
    }
}
