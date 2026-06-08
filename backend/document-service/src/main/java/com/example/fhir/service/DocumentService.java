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
 */
@Service
public class DocumentService {

    private final AiMetadataService aiMetadataService;
    private final Optional<VectorSearchService> vectorSearchService;
    private final Optional<EmbeddingService> embeddingService;
    private final DocumentRepository documentRepository;
    private final boolean useJpa;

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
    // LOINC code mapping for document types
    // -----------------------------------------------------------------------

    private static final Map<String, String> LOINC_DOC_MAP = Map.ofEntries(
            Map.entry("discharge summary", "18842-5"),
            Map.entry("radiology report", "18748-4"),
            Map.entry("laboratory report", "26436-6"),
            Map.entry("clinical note", "34108-1"),
            Map.entry("imaging", "18748-4"),
            Map.entry("lab", "26436-6"),
            Map.entry("progress note", "11506-3"),
            Map.entry("consultation", "11488-3"),
            Map.entry("procedure note", "28570-0"),
            Map.entry("pathology report", "11526-1"));

    private static final Map<String, String> LOINC_CATEGORY_MAP = Map.ofEntries(
            Map.entry("clinical-note", "LP173421-1"),
            Map.entry("imaging", "LP29693-6"),
            Map.entry("lab", "LP7839-6"),
            Map.entry("pathology", "LP7839-6"),
            Map.entry("cardiology", "LP104197-4"));

    private String lookupLoincCode(String documentType, Map<String, String> map) {
        if (documentType == null) return null;
        String key = documentType.toLowerCase().trim();
        return map.entrySet().stream()
                .filter(e -> key.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String loincDisplayForCode(String code) {
        return switch (code) {
            case "18842-5" -> "Discharge summary";
            case "18748-4" -> "Diagnostic imaging report";
            case "26436-6" -> "Laboratory report";
            case "34108-1" -> "Clinical note";
            case "11506-3" -> "Progress note";
            case "11488-3" -> "Consultation note";
            case "28570-0" -> "Procedure note";
            case "11526-1" -> "Pathology report";
            case "LP173421-1" -> "Clinical note";
            case "LP29693-6" -> "Imaging";
            case "LP7839-6" -> "Laboratory";
            case "LP104197-4" -> "Cardiology";
            default -> code;
        };
    }

    // -----------------------------------------------------------------------
    // Seed data (only for non-JPA in-memory mode)
    // -----------------------------------------------------------------------

    @PostConstruct
    void loadSeedDocuments() {
        if (useJpa) {
            return;
        }
        saveSeed(new ClinicalDocument(
                "doc-001", "PAT-1001", "Asha Rao",
                "Cardiology discharge summary", "Discharge summary",
                "clinical-note", "application/pdf",
                "https://example.org/documents/doc-001.pdf",
                Instant.parse("2025-11-03T10:15:00Z"),
                "Dr. Meera Iyer", "EHR",
                "Patient discharged after cardiology observation with medication reconciliation and follow-up advice.",
                List.of("cardiology", "discharge", "medication", "follow-up"),
                "ENC-CARD-001", "Cardiology admission visit 2025-11-01",
                Instant.parse("2025-11-01T08:00:00Z"), Instant.parse("2025-11-03T10:15:00Z"),
                "Cardiology Department", "Cardiology", "NORMAL"));
        saveSeed(new ClinicalDocument(
                "doc-002", "PAT-1002", "Rahul Menon",
                "Radiology chest imaging report", "Radiology report",
                "imaging", "application/pdf",
                "https://example.org/documents/doc-002.pdf",
                Instant.parse("2025-11-06T08:30:00Z"),
                "City Imaging Center", "PACS",
                "Chest imaging report with no acute fracture and recommendations for clinical correlation.",
                List.of("radiology", "imaging", "fracture"),
                "ENC-RAD-002", "Radiology visit 2025-11-06",
                Instant.parse("2025-11-06T08:00:00Z"), Instant.parse("2025-11-06T08:30:00Z"),
                "Radiology Dept", "Radiology", "NORMAL"));
        saveSeed(new ClinicalDocument(
                "doc-003", "PAT-1001", "Asha Rao",
                "Diabetes lab trend", "Laboratory report",
                "lab", "application/pdf",
                "https://example.org/documents/doc-003.pdf",
                Instant.parse("2025-11-12T14:45:00Z"),
                "Central Lab", "LIS",
                "Laboratory trend document covering diabetes monitoring, creatinine, and hemoglobin values.",
                List.of("diabetes", "lab", "creatinine", "hemoglobin"),
                "ENC-LAB-003", "Lab follow-up 2025-11-12",
                Instant.parse("2025-11-12T14:00:00Z"), Instant.parse("2025-11-12T14:45:00Z"),
                "Outpatient Lab", "Laboratory Medicine", "NORMAL"));
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    public List<ClinicalDocument> search(String patient, String type, String query) {
        if (useJpa && vectorSearchService.isPresent()) {
            return vectorSearchService.get().search(query, patient, type, 50);
        }

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
    // Ingest (create)
    // -----------------------------------------------------------------------

    public ClinicalDocument ingest(DocumentIngestRequest request) {
        EnrichedMetadata metadata = aiMetadataService.enrich(request);

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
            float[] embedding = embeddingService
                    .map(es -> es.embed(es.buildDocumentText(
                            document.getTitle(), document.getDocumentType(),
                            document.getCategory(), document.getPatientName(),
                            document.getAiSummary(), document.getAiKeywords(),
                            request.content())))
                    .orElse(null);

            DocumentEntity entity = DocumentEntity.fromClinicalDocument(document, embedding);
            documentRepository.save(entity);
        } else {
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
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fullUrl", "urn:uuid:" + document.getId());
            entry.put("resource", toFhirDocumentReference(document));
            entry.put("search", Map.of("mode", "match"));
            entries.add(entry);
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", UUID.randomUUID().toString());
        bundle.put("type", "searchset");
        bundle.put("timestamp", Instant.now().toString());
        bundle.put("total", results.size());
        bundle.put("link", List.of(
                Map.of("relation", "self", "url", "/fhir/DocumentReference")));
        bundle.put("entry", entries);
        return bundle;
    }

    public Map<String, Object> toFhirDocumentReference(ClinicalDocument document) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "DocumentReference");
        resource.put("id", document.getId());

        // masterIdentifier — required by FHIR R4
        resource.put("masterIdentifier", Map.of(
                "system", "urn:ietf:rfc:3986",
                "value", "urn:uuid:" + document.getId()));

        // identifier — additional business identifiers
        List<Map<String, Object>> identifiers = new ArrayList<>();
        identifiers.add(Map.of(
                "system", "https://example.org/fhir/identifier/document-id",
                "value", document.getId()));
        resource.put("identifier", identifiers);

        resource.put("status", "current");
        resource.put("docStatus", "final");

        // subject — proper reference
        resource.put("subject", Map.of(
                "reference", "Patient/" + document.getPatientId(),
                "display", document.getPatientName()));

        // type — with proper LOINC coding
        String loincCode = lookupLoincCode(document.getDocumentType(), LOINC_DOC_MAP);
        String typeDisplay = loincCode != null ? loincDisplayForCode(loincCode) : document.getDocumentType();
        resource.put("type", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://loinc.org",
                        "code", loincCode != null ? loincCode : "34108-1",
                        "display", typeDisplay)),
                "text", document.getDocumentType()));

        // category — with proper LOINC category mapping
        String catCode = lookupLoincCode(document.getCategory(), LOINC_CATEGORY_MAP);
        String catDisplay = catCode != null ? loincDisplayForCode(catCode) : document.getCategory();
        resource.put("category", List.of(Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/document-classcodes",
                        "code", catCode != null ? catCode : document.getCategory(),
                        "display", catDisplay)),
                "text", document.getCategory())));

        resource.put("date", document.getCreatedAt().toString());

        // author — proper references
        resource.put("author", List.of(Map.of(
                "reference", "Practitioner/" + sanitizeReference(document.getAuthor()),
                "display", document.getAuthor())));

        // custodian — proper reference
        resource.put("custodian", Map.of(
                "reference", "Organization/" + sanitizeReference(document.getSourceSystem()),
                "display", document.getSourceSystem()));

        // securityLabel
        if (document.getSecurityLabel() != null && !document.getSecurityLabel().isBlank()) {
            resource.put("securityLabel", List.of(Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/v3-Confidentiality",
                            "code", document.getSecurityLabel(),
                            "display", document.getSecurityLabel())),
                    "text", document.getSecurityLabel())));
        }

        resource.put("description", document.getAiSummary());

        // content
        resource.put("content", List.of(Map.of(
                "attachment", Map.of(
                        "contentType", document.getMimeType(),
                        "url", document.getUrl(),
                        "title", document.getTitle(),
                        "creation", document.getCreatedAt().toString()))));

        // context — encounter, period, facilityType, practiceSetting
        Map<String, Object> context = new LinkedHashMap<>();
        if (document.getEncounterId() != null) {
            context.put("encounter", List.of(Map.of(
                    "reference", "Encounter/" + document.getEncounterId(),
                    "display", firstPresent(document.getEncounterDisplay(), document.getEncounterId()))));
        }
        if (document.getPeriodStart() != null || document.getPeriodEnd() != null) {
            Map<String, Object> period = new LinkedHashMap<>();
            if (document.getPeriodStart() != null) period.put("start", document.getPeriodStart().toString());
            if (document.getPeriodEnd() != null) period.put("end", document.getPeriodEnd().toString());
            context.put("period", period);
        }
        if (document.getFacilityType() != null) {
            context.put("facilityType", Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
                            "code", sanitizeCode(document.getFacilityType()),
                            "display", document.getFacilityType())),
                    "text", document.getFacilityType()));
        }
        if (document.getPracticeSetting() != null) {
            context.put("practiceSetting", Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/practice-setting-codes",
                            "code", sanitizeCode(document.getPracticeSetting()),
                            "display", document.getPracticeSetting())),
                    "text", document.getPracticeSetting()));
        }
        if (!context.isEmpty()) {
            resource.put("context", context);
        }

        // extension — AI-assisted metadata
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

    private String sanitizeReference(String name) {
        if (name == null || name.isBlank()) return "unknown";
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String sanitizeCode(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}