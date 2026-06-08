package com.example.fhir.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

import com.example.fhir.model.ClinicalDocument;
import com.example.fhir.model.DocumentIngestRequest;

@Service
public class AiMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(AiMetadataService.class);

    private static final List<String> CLINICAL_TERMS = List.of(
            "diabetes", "hypertension", "asthma", "cardiology", "radiology", "pathology",
            "discharge", "medication", "allergy", "lab", "imaging", "follow-up",
            "creatinine", "hemoglobin", "fracture", "infection");

    private final RestClient sidecarClient;
    private final ObjectMapper objectMapper;
    private final boolean sidecarConfigured;

    public AiMetadataService(
            ObjectMapper objectMapper,
            @Value("${ai.sidecar.base-url:}") String baseUrl,
            @Value("${ai.sidecar.timeout:2s}") Duration timeout) {
        this.objectMapper = objectMapper;
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        this.sidecarConfigured = !normalizedBaseUrl.isBlank();
        this.sidecarClient = sidecarConfigured
                ? RestClient.builder()
                        .baseUrl(normalizedBaseUrl)
                        .requestFactory(createRequestFactory(timeout))
                        .build()
                : null;
    }

    public EnrichedMetadata enrich(DocumentIngestRequest request) {
        if (sidecarConfigured) {
            try {
                String requestJson = toJson(request);
                SidecarEnrichmentResponse response = sidecarClient.post()
                        .uri("/enrich")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestJson)
                        .retrieve()
                        .body(SidecarEnrichmentResponse.class);
                if (response != null) {
                    return new EnrichedMetadata(
                            firstPresent(response.title(), request.title(), inferTitle(request.content()),
                                    "Untitled clinical document"),
                            firstPresent(response.summary(), summarize(request.content())),
                            response.keywords() == null ? List.of() : response.keywords());
                }
            } catch (RestClientException ex) {
                logger.warn("AI sidecar unavailable, falling back to local enrichment: {}", ex.getMessage());
            }
        }

        return enrichLocally(request);
    }

    public int semanticScore(ClinicalDocument document, String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }

        if (sidecarConfigured) {
            try {
                String requestJson = toJson(Map.of("document", document, "query", query));
                SidecarScoreResponse response = sidecarClient.post()
                        .uri("/score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestJson)
                        .retrieve()
                        .body(SidecarScoreResponse.class);
                if (response != null) {
                    return response.score();
                }
            } catch (RestClientException ex) {
                logger.warn("AI sidecar unavailable, falling back to local semantic scoring: {}", ex.getMessage());
            }
        }

        return semanticScoreLocally(document, query);
    }

    private EnrichedMetadata enrichLocally(DocumentIngestRequest request) {
        String content = valueOrEmpty(request.content());
        String title = firstPresent(request.title(), inferTitle(content), "Untitled clinical document");
        List<String> keywords = extractKeywords(title + " " + content);
        String summary = summarize(content);
        return new EnrichedMetadata(title, summary, keywords);
    }

    private int semanticScoreLocally(ClinicalDocument document, String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        int score = 0;
        score += containsScore(document.getTitle(), normalizedQuery, 8);
        score += containsScore(document.getDocumentType(), normalizedQuery, 5);
        score += containsScore(document.getCategory(), normalizedQuery, 4);
        score += containsScore(document.getPatientName(), normalizedQuery, 4);
        score += containsScore(document.getAiSummary(), normalizedQuery, 3);

        for (String keyword : document.getAiKeywords()) {
            score += containsScore(keyword, normalizedQuery, 6);
        }

        return score;
    }

    private List<String> extractKeywords(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> keywords = new LinkedHashSet<>();

        for (String term : CLINICAL_TERMS) {
            if (normalized.contains(term)) {
                keywords.add(term);
            }
        }

        String[] tokens = normalized.replaceAll("[^a-z0-9 -]", " ").split("\\s+");
        List<String> candidates = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() > 5 && !isStopWord(token)) {
                candidates.add(token);
            }
        }

        candidates.stream()
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(4)
                .forEach(keywords::add);

        return keywords.stream().limit(8).toList();
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "AI sidecar has no document body yet; metadata is indexed from the submitted title and fields.";
        }

        String compact = content.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 180) {
            return compact;
        }

        return compact.substring(0, 177) + "...";
    }

    private String inferTitle(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String compact = content.replaceAll("\\s+", " ").trim();
        int end = Math.min(compact.length(), 72);
        return compact.substring(0, end);
    }

    private int containsScore(String value, String query, int score) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query) ? score : 0;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isStopWord(String token) {
        return List.of("patient", "clinical", "document", "report", "normal", "history").contains(token);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }

        String trimmed = baseUrl.trim();
        if (trimmed.isBlank()) {
            return "";
        }

        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private SimpleClientHttpRequestFactory createRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1000, timeout.toMillis()));
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return requestFactory;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize AI sidecar request", ex);
        }
    }

    public record EnrichedMetadata(String title, String summary, List<String> keywords) {
    }

    private record SidecarEnrichmentResponse(String title, String summary, List<String> keywords) {
    }

    private record SidecarScoreResponse(int score) {
    }
}
