package com.example.fhir.model;

import java.time.Instant;

public record DocumentIngestRequest(
        String patientId,
        String patientName,
        String title,
        String documentType,
        String category,
        String mimeType,
        String url,
        String author,
        String sourceSystem,
        String content,
        // FHIR context fields (optional)
        String encounterId,
        String encounterDisplay,
        Instant periodStart,
        Instant periodEnd,
        String facilityType,
        String practiceSetting,
        String securityLabel) {

    public DocumentIngestRequest {
        // Normalize nulls to sensible defaults
        if (encounterId == null) encounterId = null;
        if (encounterDisplay == null) encounterDisplay = null;
        if (periodStart == null) periodStart = null;
        if (periodEnd == null) periodEnd = null;
        if (facilityType == null) facilityType = null;
        if (practiceSetting == null) practiceSetting = null;
        if (securityLabel == null) securityLabel = null;
    }
}