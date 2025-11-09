package com.hhplus.ecommerce.infrastructure.converter;

import com.hhplus.ecommerce.domain.vo.Phone;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PhoneConverter implements AttributeConverter<Phone, String> {

    @Override
    public String convertToDatabaseColumn(Phone attribute) {
        return attribute == null ? null : attribute.getNumber();
    }

    @Override
    public Phone convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Phone(dbData);
    }
}
