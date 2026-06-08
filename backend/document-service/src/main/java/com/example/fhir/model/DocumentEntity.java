package com.example.fhir.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * JPA entity for clinical documents with a pgvector embedding column.
 * The embedding is stored as a float[] and converted via a custom converter.
 */
@Entity
@Table(name = "clinical_documents", indexes = {
    @Index(name = "idx_doc_patient", columnList = "patientId"),
    @Index(name = "idx_doc_type", columnList = "documentType"),
    @Index(name = "idx_doc_category", columnList = "category")
})
public class DocumentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String patientId;

    @Column(length = 128)
    private String patientName;

    @Column(length = 256)
    private String title;

    @Column(length = 64)
    private String documentType;

    @Column(length = 64)
    private String category;

    @Column(length = 64)
    private String mimeType;

    @Column(length = 1024)
    private String url;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 256)
    private String author;

    @Column(length = 128)
    private String sourceSystem;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    /** JSON-serialized list of keywords, e.g. ["diabetes","hypertension"] */
    @Column(length = 2048)
    private String aiKeywordsJson;

    /** pgvector embedding (768 dimensions for nomic-embed-text). Stored as string for portability. */
    @Column(name = "embedding", columnDefinition = "TEXT")
    @Convert(converter = com.example.fhir.config.VectorConverter.class)
    private float[] embedding;

    // FHIR context fields
    @Column(length = 64)
    private String encounterId;

    @Column(length = 256)
    private String encounterDisplay;

    private Instant periodStart;
    private Instant periodEnd;

    @Column(length = 128)
    private String facilityType;

    @Column(length = 128)
    private String practiceSetting;

    @Column(length = 64)
    private String securityLabel;

    public DocumentEntity() {
    }

    public DocumentEntity(String id, String patientId, String patientName, String title,
                          String documentType, String category, String mimeType, String url,
                          Instant createdAt, String author, String sourceSystem,
                          String aiSummary, String aiKeywordsJson, float[] embedding,
                          String encounterId, String encounterDisplay,
                          Instant periodStart, Instant periodEnd,
                          String facilityType, String practiceSetting,
                          String securityLabel) {
        this.id = id;
        this.patientId = patientId;
        this.patientName = patientName;
        this.title = title;
        this.documentType = documentType;
        this.category = category;
        this.mimeType = mimeType;
        this.url = url;
        this.createdAt = createdAt;
        this.author = author;
        this.sourceSystem = sourceSystem;
        this.aiSummary = aiSummary;
        this.aiKeywordsJson = aiKeywordsJson;
        this.embedding = embedding;
        this.encounterId = encounterId;
        this.encounterDisplay = encounterDisplay;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.facilityType = facilityType;
        this.practiceSetting = practiceSetting;
        this.securityLabel = securityLabel;
    }

    // --- Convert from ClinicalDocument POJO ---
    public static DocumentEntity fromClinicalDocument(ClinicalDocument doc, float[] embedding) {
        return new DocumentEntity(
            doc.getId(),
            doc.getPatientId(),
            doc.getPatientName(),
            doc.getTitle(),
            doc.getDocumentType(),
            doc.getCategory(),
            doc.getMimeType(),
            doc.getUrl(),
            doc.getCreatedAt(),
            doc.getAuthor(),
            doc.getSourceSystem(),
            doc.getAiSummary(),
            keywordsToJson(doc.getAiKeywords()),
            embedding,
            doc.getEncounterId(),
            doc.getEncounterDisplay(),
            doc.getPeriodStart(),
            doc.getPeriodEnd(),
            doc.getFacilityType(),
            doc.getPracticeSetting(),
            doc.getSecurityLabel()
        );
    }

    // --- Convert to ClinicalDocument POJO ---
    public ClinicalDocument toClinicalDocument() {
        ClinicalDocument doc = new ClinicalDocument();
        doc.setId(this.id);
        doc.setPatientId(this.patientId);
        doc.setPatientName(this.patientName);
        doc.setTitle(this.title);
        doc.setDocumentType(this.documentType);
        doc.setCategory(this.category);
        doc.setMimeType(this.mimeType);
        doc.setUrl(this.url);
        doc.setCreatedAt(this.createdAt);
        doc.setAuthor(this.author);
        doc.setSourceSystem(this.sourceSystem);
        doc.setAiSummary(this.aiSummary);
        doc.setAiKeywords(keywordsFromJson(this.aiKeywordsJson));
        doc.setEncounterId(this.encounterId);
        doc.setEncounterDisplay(this.encounterDisplay);
        doc.setPeriodStart(this.periodStart);
        doc.setPeriodEnd(this.periodEnd);
        doc.setFacilityType(this.facilityType);
        doc.setPracticeSetting(this.practiceSetting);
        doc.setSecurityLabel(this.securityLabel);
        return doc;
    }

    // --- Keyword JSON helpers ---
    public static String keywordsToJson(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(keywords.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<String> keywordsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        String inner = json.substring(1, json.length() - 1);
        return java.util.Arrays.stream(inner.split(","))
            .map(s -> s.trim().replaceAll("^\"|\"$", ""))
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // --- Getters / Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getAiKeywordsJson() { return aiKeywordsJson; }
    public void setAiKeywordsJson(String aiKeywordsJson) { this.aiKeywordsJson = aiKeywordsJson; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public String getEncounterDisplay() { return encounterDisplay; }
    public void setEncounterDisplay(String encounterDisplay) { this.encounterDisplay = encounterDisplay; }

    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }

    public String getPracticeSetting() { return practiceSetting; }
    public void setPracticeSetting(String practiceSetting) { this.practiceSetting = practiceSetting; }

    public String getSecurityLabel() { return securityLabel; }
    public void setSecurityLabel(String securityLabel) { this.securityLabel = securityLabel; }
}