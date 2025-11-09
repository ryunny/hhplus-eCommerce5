package com.hhplus.ecommerce.infrastructure.converter;

import com.hhplus.ecommerce.domain.vo.Stock;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StockConverter implements AttributeConverter<Stock, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Stock attribute) {
        return attribute == null ? null : attribute.getQuantity();
    }

    @Override
    public Stock convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new Stock(dbData);
    }
}
