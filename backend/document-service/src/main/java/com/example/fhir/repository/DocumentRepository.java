package com.example.fhir.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.fhir.model.DocumentEntity;

/**
 * Spring Data JPA repository for clinical documents.
 * Supports pgvector-based similarity search via native SQL.
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findByPatientId(String patientId);

    List<DocumentEntity> findByDocumentType(String documentType);

    List<DocumentEntity> findByCategory(String category);

    @Query("SELECT d FROM DocumentEntity d WHERE d.patientId = :patientId OR d.patientName LIKE %:name%")
    List<DocumentEntity> findByPatient(@Param("patientId") String patientId, @Param("name") String name);

    /**
     * pgvector cosine similarity search using the <=> operator.
     * Returns the top-N documents closest to the query embedding vector.
     * Works only when pgvector extension is installed.
     */
    @Query(value = """
        SELECT *, embedding <=> CAST(:queryVector AS vector) AS distance
        FROM clinical_documents
        WHERE embedding IS NOT NULL
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (:type IS NULL OR document_type = :type)
        ORDER BY embedding <=> CAST(:queryVector AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentEntity> findSimilarByVector(
        @Param("queryVector") String queryVector,
        @Param("patientId") String patientId,
        @Param("type") String type,
        @Param("limit") int limit
    );

    /**
     * Count documents that have embeddings (for indexing decisions).
     */
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.embedding IS NOT NULL")
    long countWithEmbeddings();
}