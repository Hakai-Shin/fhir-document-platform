package com.example.fhir.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

import com.example.fhir.model.ClinicalDocument;
import com.example.fhir.model.DocumentIngestRequest;
import com.example.fhir.service.AiMetadataService.EnrichedMetadata;

@Service
public class DocumentService {

    private final AiMetadataService aiMetadataService;
    private final Map<String, ClinicalDocument> documents = new ConcurrentHashMap<>();

    public DocumentService(AiMetadataService aiMetadataService) {
        this.aiMetadataService = aiMetadataService;
    }

    @PostConstruct
    void loadSeedDocuments() {
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

    public List<ClinicalDocument> search(String patient, String type, String query) {
        return documents.values().stream()
                .filter(document -> matches(document.getPatientId(), patient) || matches(document.getPatientName(), patient)
                        || patient == null || patient.isBlank())
                .filter(document -> matches(document.getDocumentType(), type) || matches(document.getCategory(), type)
                        || type == null || type.isBlank())
                .filter(document -> query == null || query.isBlank() || aiMetadataService.semanticScore(document, query) > 0)
                .sorted(Comparator
                        .comparingInt((ClinicalDocument document) -> aiMetadataService.semanticScore(document, query))
                        .reversed()
                        .thenComparing(ClinicalDocument::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    public ClinicalDocument findById(String id) {
        return Optional.ofNullable(documents.get(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DocumentReference not found"));
    }

    public ClinicalDocument ingest(DocumentIngestRequest request) {
        EnrichedMetadata metadata = aiMetadataService.enrich(request);
        ClinicalDocument document = new ClinicalDocument(
                "doc-" + UUID.randomUUID(),
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

        documents.put(document.getId(), document);
        return document;
    }

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
        resource.put("type", coding("http://loinc.org", document.getDocumentType(), document.getDocumentType()));
        resource.put("category", List.of(coding("http://terminology.hl7.org/CodeSystem/document-classcodes",
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
        return value != null && query != null && value.toLowerCase().contains(query.toLowerCase());
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
