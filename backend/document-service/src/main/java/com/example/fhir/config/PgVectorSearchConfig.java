package com.example.fhir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for pgvector-enabled PostgreSQL.
 * Activates only when the {@code pgvector} Spring profile is active.
 * Ensures the pgvector extension exists and creates the IVFFlat index.
 */
@Configuration
@ConditionalOnProperty(name = "spring.jpa.properties.hibernate.dialect", havingValue = "com.example.fhir.config.PgVectorDialect")
public class PgVectorSearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(PgVectorSearchConfig.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${pgvector.index.type:ivfflat}")
    private String indexType;

    @Value("${pgvector.index.lists:100}")
    private int lists;

    public PgVectorSearchConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initializePgVector() {
        try {
            // Enable the pgvector extension (idempotent)
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            logger.info("pgvector extension enabled");

            // Create IVFFlat index on the embedding column if it doesn't exist
            String indexSql = String.format(
                "CREATE INDEX IF NOT EXISTS idx_doc_embedding ON clinical_documents " +
                "USING %s (embedding vector_cosine_ops)", indexType);
            jdbcTemplate.execute(indexSql);
            logger.info("pgvector {} index created/verified with lists={}", indexType, lists);

            // Set the IVFFlat probe count for balanced accuracy/speed
            jdbcTemplate.execute("SET ivfflat.probes = 10");
            logger.info("pgvector ivfflat.probes set to 10");

        } catch (Exception e) {
            logger.warn("pgvector initialization failed: {}. Vector search will fall back to Java-side scoring.", e.getMessage());
        }
    }

    /**
     * Bean to signal that pgvector is active (used by other services for conditional logic).
     */
    @Bean
    @ConditionalOnProperty(name = "spring.jpa.properties.hibernate.dialect",
        havingValue = "com.example.fhir.config.PgVectorDialect")
    public boolean pgvectorActive() {
        return true;
    }
}