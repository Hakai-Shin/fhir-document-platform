package com.example.fhir.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ClinicalDocument {

    private String id;
    private String patientId;
    private String patientName;
    private String title;
    private String documentType;
    private String category;
    private String mimeType;
    private String url;
    private Instant createdAt;
    private String author;
    private String sourceSystem;
    private String aiSummary;
    private List<String> aiKeywords = new ArrayList<>();

    // FHIR context fields
    private String encounterId;
    private String encounterDisplay;
    private Instant periodStart;
    private Instant periodEnd;
    private String facilityType;
    private String practiceSetting;
    private String securityLabel;

    public ClinicalDocument() {
    }

    public ClinicalDocument(String id, String patientId, String patientName, String title, String documentType,
            String category, String mimeType, String url, Instant createdAt, String author, String sourceSystem,
            String aiSummary, List<String> aiKeywords) {
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
        this.aiKeywords = aiKeywords == null ? new ArrayList<>() : new ArrayList<>(aiKeywords);
    }

    /**
     * Full constructor including FHIR context fields.
     */
    public ClinicalDocument(String id, String patientId, String patientName, String title, String documentType,
            String category, String mimeType, String url, Instant createdAt, String author, String sourceSystem,
            String aiSummary, List<String> aiKeywords,
            String encounterId, String encounterDisplay,
            Instant periodStart, Instant periodEnd,
            String facilityType, String practiceSetting,
            String securityLabel) {
        this(id, patientId, patientName, title, documentType, category, mimeType, url, createdAt,
                author, sourceSystem, aiSummary, aiKeywords);
        this.encounterId = encounterId;
        this.encounterDisplay = encounterDisplay;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.facilityType = facilityType;
        this.practiceSetting = practiceSetting;
        this.securityLabel = securityLabel;
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

    public List<String> getAiKeywords() { return aiKeywords; }
    public void setAiKeywords(List<String> aiKeywords) { this.aiKeywords = aiKeywords == null ? new ArrayList<>() : new ArrayList<>(aiKeywords); }

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