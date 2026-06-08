package com.example.fhir.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Service that calls the AI sidecar's {@code /embed} endpoint to generate
 * embedding vectors for document text. Used by the pgvector search path.
 */
@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestClient sidecarClient;
    private final ObjectMapper objectMapper;
    private final boolean sidecarConfigured;

    public EmbeddingService(
            ObjectMapper objectMapper,
            @Value("${ai.sidecar.base-url:}") String baseUrl,
            @Value("${ai.sidecar.timeout:10s}") Duration timeout) {
        this.objectMapper = objectMapper;
        String normalized = normalizeBaseUrl(baseUrl);
        this.sidecarConfigured = !normalized.isBlank();
        this.sidecarClient = sidecarConfigured
                ? RestClient.builder()
                        .baseUrl(normalized)
                        .requestFactory(createRequestFactory(timeout))
                        .build()
                : null;
    }

    /**
     * Generate an embedding vector for the given text.
     * Falls back to returning {@code null} if the sidecar is unavailable.
     */
    public float[] embed(String text) {
        if (!sidecarConfigured || text == null || text.isBlank()) {
            return null;
        }
        try {
            String requestJson = objectMapper.writeValueAsString(Map.of("text", text));
            EmbedResponse response = sidecarClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(EmbedResponse.class);
            if (response != null && response.embedding() != null && !response.embedding().isEmpty()) {
                float[] result = new float[response.embedding().size()];
                for (int i = 0; i < response.embedding().size(); i++) {
                    result[i] = response.embedding().get(i).floatValue();
                }
                return result;
            }
        } catch (RestClientException | JsonProcessingException e) {
            logger.warn("Embedding service unavailable: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Build a combined document text suitable for embedding.
     * Mirrors the AI sidecar's {@code /score} document text construction.
     */
    public String buildDocumentText(String title, String documentType, String category,
                                     String patientName, String summary, List<String> keywords, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(" ");
        if (documentType != null) sb.append(documentType).append(" ");
        if (category != null) sb.append(category).append(" ");
        if (patientName != null) sb.append(patientName).append(" ");
        if (summary != null) sb.append(summary).append(" ");
        if (keywords != null) keywords.forEach(k -> sb.append(k).append(" "));
        if (content != null) sb.append(content);
        return sb.toString().trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String trimmed = baseUrl.trim();
        if (trimmed.isBlank()) return "";
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private SimpleClientHttpRequestFactory createRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        int ms = (int) Math.min(Integer.MAX_VALUE, Math.max(1000, timeout.toMillis()));
        rf.setConnectTimeout(ms);
        rf.setReadTimeout(ms);
        return rf;
    }

    private record EmbedResponse(List<Number> embedding) {
    }
}