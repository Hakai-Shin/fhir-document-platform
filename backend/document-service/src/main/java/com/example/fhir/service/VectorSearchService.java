package com.example.fhir.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.fhir.model.ClinicalDocument;
import com.example.fhir.model.DocumentEntity;
import com.example.fhir.repository.DocumentRepository;

/**
 * Service that performs pgvector-based cosine similarity search.
 * Only active when the {@code pgvector} profile is enabled.
 * Falls back gracefully if pgvector initialization failed.
 */
@Service
@ConditionalOnProperty(name = "spring.jpa.properties.hibernate.dialect",
    havingValue = "com.example.fhir.config.PgVectorDialect")
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    private final DocumentRepository repository;
    private final EmbeddingService embeddingService;

    public VectorSearchService(DocumentRepository repository, EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /**
     * Search documents by embedding similarity.
     * Returns up to {@code limit} results sorted by cosine distance (ascending).
     */
    public List<ClinicalDocument> search(String query, String patientId, String type, int limit) {
        if (query == null || query.isBlank()) {
            // No query — fall back to simple DB search
            if (patientId != null && !patientId.isBlank()) {
                return repository.findByPatient(patientId, patientId)
                    .stream().map(DocumentEntity::toClinicalDocument).toList();
            }
            if (type != null && !type.isBlank()) {
                return repository.findByDocumentType(type)
                    .stream().map(DocumentEntity::toClinicalDocument).toList();
            }
            return repository.findAll().stream().map(DocumentEntity::toClinicalDocument).toList();
        }

        // Generate embedding for the query
        float[] queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding == null) {
            logger.warn("Vector search: embedding unavailable, returning empty results");
            return List.of();
        }

        // Convert embedding to pgvector string format: [0.1,0.2,...]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queryEmbedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(queryEmbedding[i]);
        }
        sb.append("]");
        String vectorStr = sb.toString();

        // Execute pgvector similarity search
        try {
            return repository.findSimilarByVector(vectorStr,
                    patientId != null && !patientId.isBlank() ? patientId : null,
                    type != null && !type.isBlank() ? type : null,
                    limit)
                .stream().map(DocumentEntity::toClinicalDocument).toList();
        } catch (Exception e) {
            logger.error("Vector search query failed: {}", e.getMessage());
            return List.of();
        }
    }
}