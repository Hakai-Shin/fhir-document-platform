package com.example.fhir.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter that serializes a {@code float[]} embedding to a
 * string format {@code [0.1,0.2,...]} for storage, and deserializes back.
 *
 * <p>For the {@code pgvector} profile, the string is cast to {@code vector}
 * type in native SQL queries via {@code CAST(:vector AS vector)}. For the
 * default (H2) profile, it's stored as a regular TEXT column.</p>
 */
@Converter(autoApply = false)
public class VectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(attribute[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || dbData.equals("[]")) {
            return null;
        }
        String inner = dbData.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        // Also strip possible pgvector extra brackets like "[0.1,0.2]::vector"
        if (inner.endsWith("::vector")) {
            inner = inner.substring(0, inner.length() - 9);
        }
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}