package com.finbase.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GoldPurityConverter implements AttributeConverter<GoldPurity, String> {

    @Override
    public String convertToDatabaseColumn(GoldPurity attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public GoldPurity convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GoldPurity.fromDbValue(dbData);
    }
}
