package com.example.fhir.document_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.fhir.model.ClinicalDocument;
import com.example.fhir.model.DocumentIngestRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentServiceApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void exposesDocumentReferencesAsFhirBundle() {
        String response = restTemplate.getForObject(url("/fhir/DocumentReference?patient=PAT-1001&_text=lab"),
                String.class);

        assertThat(response).contains("\"resourceType\":\"Bundle\"");
        assertThat(response).contains("\"resourceType\":\"DocumentReference\"");
        assertThat(response).contains("Diabetes lab trend");
    }

    @Test
    void ingestsDocumentWithAiAssistedMetadata() {
        DocumentIngestRequest request = new DocumentIngestRequest(
                "PAT-2001",
                "Kiran Shah",
                "Asthma medication review",
                "Progress note",
                "clinical-note",
                "application/pdf",
                "https://example.org/documents/asthma-review.pdf",
                "Dr. Sen",
                "Clinic",
                "Asthma follow-up document with medication review and allergy check.");

        ClinicalDocument created = restTemplate.postForObject(url("/api/document"), request, ClinicalDocument.class);

        assertThat(created.getId()).startsWith("doc-");
        assertThat(created.getAiKeywords()).contains("asthma", "medication", "allergy");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
