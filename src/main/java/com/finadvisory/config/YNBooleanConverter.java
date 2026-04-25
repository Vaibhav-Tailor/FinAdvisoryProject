package com.finadvisory.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that maps Java Boolean <-> DB CHAR(1) Y/N.
 * Applied automatically to all Boolean fields annotated with
 * @Convert(converter = YNBooleanConverter.class)
 *
 * DB stores : 'Y' = true  |  'N' = false
 * Java uses : true / false (standard Boolean)
 */
@Converter
public class YNBooleanConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) return "N";
        return attribute ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        if (dbData == null) return Boolean.FALSE;
        return "Y".equalsIgnoreCase(dbData.trim());
    }
}
