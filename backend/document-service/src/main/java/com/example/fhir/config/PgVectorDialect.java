package com.example.fhir.config;

import org.hibernate.dialect.PostgreSQLDialect;

/**
 * Custom Hibernate dialect that registers the pgvector {@code vector} type.
 * This enables Hibernate to map {@code vector(n)} columns correctly.
 *
 * <p>For vector storage we use a custom {@link VectorConverter} that
 * serializes {@code float[]} to a string format. In native pgvector queries
 * the string is cast to vector via {@code CAST(:string AS vector)}.</p>
 */
public class PgVectorDialect extends PostgreSQLDialect {

    public PgVectorDialect() {
        super();
    }
}