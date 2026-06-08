package com.example.fhir.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.fhir.model.ClinicalDocument;
import com.example.fhir.model.DocumentEntity;
import com.example.fhir.model.DocumentIngestRequest;
import com.example.fhir.repository.DocumentRepository;
import com.example.fhir.service.AiMetadataService.EnrichedMetadata;

/**
 * Core document service that orchestrates persistence, AI enrichment,
 * semantic search, and FHIR conversion.
 *
 * <p>When the {@code pgvector} profile is active, documents are persisted
 * in PostgreSQL via JPA and search uses pgvector cosine similarity.
 * Otherwise, an in-memory {@code ConcurrentHashMap} is used with the
 * {@code semanticScore()} fallback from {@link AiMetadataService}.</p>
 */
@Service
public class DocumentService {

    private final AiMetadataService aiMetadataService;
    private final Optional<VectorSearchService> vectorSearchService;
    private final Optional<EmbeddingService> embeddingService;
    private final DocumentRepository documentRepository;
    private final boolean useJpa;

    // In-memory fallback store (used when JPA/pgvector is not active)
    private final Map<String, ClinicalDocument> documents = new java.util.concurrent.ConcurrentHashMap<>();

    public DocumentService(
            AiMetadataService aiMetadataService,
            Optional<VectorSearchService> vectorSearchService,
            Optional<EmbeddingService> embeddingService,
            DocumentRepository documentRepository) {
        this.aiMetadataService = aiMetadataService;
        this.vectorSearchService = vectorSearchService;
        this.embeddingService = embeddingService;
        this.documentRepository = documentRepository;
        this.useJpa = vectorSearchService.isPresent();
    }

    // -----------------------------------------------------------------------
    // Seed data (only for non-JPA in-memory mode)
    // -----------------------------------------------------------------------

    @PostConstruct
    void loadSeedDocuments() {
        if (useJpa) {
            return; // JPA mode: data comes from DB
        }
        saveSeed(new ClinicalDocument(
                "doc-001",
                "PAT-1001",
                "Asha Rao",
                "Cardiology discharge summary",
                "Discharge summary",
                "clinical-note",
                "application/pdf",
                "https://example.org/documents/doc-001.pdf",
                Instant.parse("2025-11-03T10:15:00Z"),
                "Dr. Meera Iyer",
                "EHR",
                "Patient discharged after cardiology observation with medication reconciliation and follow-up advice.",
                List.of("cardiology", "discharge", "medication", "follow-up")));
        saveSeed(new ClinicalDocument(
                "doc-002",
                "PAT-1002",
                "Rahul Menon",
                "Radiology chest imaging report",
                "Radiology report",
                "imaging",
                "application/pdf",
                "https://example.org/documents/doc-002.pdf",
                Instant.parse("2025-11-06T08:30:00Z"),
                "City Imaging Center",
                "PACS",
                "Chest imaging report with no acute fracture and recommendations for clinical correlation.",
                List.of("radiology", "imaging", "fracture")));
        saveSeed(new ClinicalDocument(
                "doc-003",
                "PAT-1001",
                "Asha Rao",
                "Diabetes lab trend",
                "Laboratory report",
                "lab",
                "application/pdf",
                "https://example.org/documents/doc-003.pdf",
                Instant.parse("2025-11-12T14:45:00Z"),
                "Central Lab",
                "LIS",
                "Laboratory trend document covering diabetes monitoring, creatinine, and hemoglobin values.",
                List.of("diabetes", "lab", "creatinine", "hemoglobin")));
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    public List<ClinicalDocument> search(String patient, String type, String query) {
        if (useJpa && vectorSearchService.isPresent()) {
            // pgvector search path — fast, single SQL query with <=> operator
            return vectorSearchService.get().search(query, patient, type, 50);
        }

        // Fallback: in-memory search with local semantic scoring
        return documents.values().stream()
                .filter(d -> matches(d.getPatientId(), patient)
                        || matches(d.getPatientName(), patient)
                        || patient == null || patient.isBlank())
                .filter(d -> matches(d.getDocumentType(), type)
                        || matches(d.getCategory(), type)
                        || type == null || type.isBlank())
                .filter(d -> query == null || query.isBlank()
                        || aiMetadataService.semanticScore(d, query) > 0)
                .sorted(java.util.Comparator
                        .<ClinicalDocument>comparingDouble(
                                d -> aiMetadataService.semanticScore(d, query))
                        .reversed()
                        .thenComparing(ClinicalDocument::getCreatedAt,
                                java.util.Comparator.reverseOrder()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Find by ID
    // -----------------------------------------------------------------------

    public ClinicalDocument findById(String id) {
        if (useJpa) {
            return documentRepository.findById(id)
                    .map(DocumentEntity::toClinicalDocument)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Document not found"));
        }
        return Optional.ofNullable(documents.get(id))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found"));
    }

    // -----------------------------------------------------------------------
    // Ingest (create) — enrich + persist + vectorize
    // -----------------------------------------------------------------------

    public ClinicalDocument ingest(DocumentIngestRequest request) {
        // 1. Enrich with AI (title, summary, keywords)
        EnrichedMetadata metadata = aiMetadataService.enrich(request);

        // 2. Build the document POJO
        String docId = "doc-" + UUID.randomUUID();
        ClinicalDocument document = new ClinicalDocument(
                docId,
                required(request.patientId(), "patientId"),
                firstPresent(request.patientName(), "Unknown patient"),
                metadata.title(),
                firstPresent(request.documentType(), "Clinical document"),
                firstPresent(request.category(), "clinical-note"),
                firstPresent(request.mimeType(), "application/pdf"),
                firstPresent(request.url(), "https://example.org/documents/pending"),
                Instant.now(),
                firstPresent(request.author(), "Unknown author"),
                firstPresent(request.sourceSystem(), "Manual upload"),
                metadata.summary(),
                metadata.keywords());

        if (useJpa) {
            // 3a. Generate embedding via AI sidecar (if available)
            float[] embedding = embeddingService
                    .map(es -> es.embed(es.buildDocumentText(
                            document.getTitle(), document.getDocumentType(),
                            document.getCategory(), document.getPatientName(),
                            document.getAiSummary(), document.getAiKeywords(),
                            request.content())))
                    .orElse(null);

            // 4a. Persist via JPA
            DocumentEntity entity = DocumentEntity.fromClinicalDocument(document, embedding);
            documentRepository.save(entity);
        } else {
            // 3b. Store in memory
            documents.put(document.getId(), document);
        }

        return document;
    }

    // -----------------------------------------------------------------------
    // FHIR conversion
    // -----------------------------------------------------------------------

    public Map<String, Object> toFhirBundle(List<ClinicalDocument> results) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ClinicalDocument document : results) {
            entries.add(Map.of(
                    "fullUrl", "urn:uuid:" + document.getId(),
                    "resource", toFhirDocumentReference(document)));
        }
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");
        bundle.put("total", results.size());
        bundle.put("entry", entries);
        return bundle;
    }

    public Map<String, Object> toFhirDocumentReference(ClinicalDocument document) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "DocumentReference");
        resource.put("id", document.getId());
        resource.put("status", "current");
        resource.put("docStatus", "final");
        resource.put("subject", Map.of(
                "reference", "Patient/" + document.getPatientId(),
                "display", document.getPatientName()));
        resource.put("type", coding("http://loinc.org",
                document.getDocumentType(), document.getDocumentType()));
        resource.put("category", List.of(coding(
                "http://terminology.hl7.org/CodeSystem/document-classcodes",
                document.getCategory(), document.getCategory())));
        resource.put("date", document.getCreatedAt().toString());
        resource.put("author", List.of(Map.of("display", document.getAuthor())));
        resource.put("custodian", Map.of("display", document.getSourceSystem()));
        resource.put("description", document.getAiSummary());
        resource.put("content", List.of(Map.of(
                "attachment", Map.of(
                        "contentType", document.getMimeType(),
                        "url", document.getUrl(),
                        "title", document.getTitle(),
                        "creation", document.getCreatedAt().toString()))));
        resource.put("extension", List.of(Map.of(
                "url", "https://example.org/fhir/StructureDefinition/ai-assisted-metadata",
                "valueString", String.join(", ", document.getAiKeywords()))));
        return resource;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void saveSeed(ClinicalDocument document) {
        documents.put(document.getId(), document);
    }

    private Map<String, Object> coding(String system, String code, String display) {
        return Map.of("coding", List.of(Map.of(
                "system", system,
                "code", code.toLowerCase().replaceAll("[^a-z0-9]+", "-"),
                "display", display)));
    }

    private boolean matches(String value, String query) {
        return value != null && query != null
                && value.toLowerCase().contains(query.toLowerCase());
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}