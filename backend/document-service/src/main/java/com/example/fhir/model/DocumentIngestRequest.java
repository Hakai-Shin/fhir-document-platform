package com.example.fhir.model;

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
        String content) {
}
