package com.example.fhir.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.fhir.model.ClinicalDocument;
import com.example.fhir.model.DocumentIngestRequest;
import com.example.fhir.service.DocumentService;

@RestController
@CrossOrigin(origins = { "http://localhost:5173", "http://127.0.0.1:5173" })
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/document")
    public List<ClinicalDocument> searchDocuments(
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, name = "q") String query) {
        return documentService.search(patient, type, query);
    }

    @GetMapping("/api/document/{id}")
    public ClinicalDocument getDocument(@PathVariable String id) {
        return documentService.findById(id);
    }

    @PostMapping("/api/document")
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicalDocument ingestDocument(@RequestBody DocumentIngestRequest request) {
        return documentService.ingest(request);
    }

    @GetMapping("/fhir/DocumentReference")
    public Map<String, Object> searchDocumentReferences(
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, name = "_text") String query) {
        return documentService.toFhirBundle(documentService.search(patient, type, query));
    }

    @GetMapping("/fhir/DocumentReference/{id}")
    public Map<String, Object> getDocumentReference(@PathVariable String id) {
        return documentService.toFhirDocumentReference(documentService.findById(id));
    }
}
