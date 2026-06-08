# Document Service

The backend document service for the FHIR Document Platform. A Spring Boot 3.5.9 application (Java 17) that exposes clinical documents through both application-friendly JSON REST endpoints and FHIR R4 `DocumentReference` APIs.

## Architecture

The service operates in two modes:

### Default Mode (in-memory)
- Uses a `ConcurrentHashMap` as the document store
- Seeds 3 sample clinical documents at startup (doc-001, doc-002, doc-003)
- AI enrichment falls back to local deterministic rules when the AI sidecar is unavailable
- Semantic search uses a local keyword-scoring algorithm

### pgvector Mode (PostgreSQL)
- Activated with the `pgvector` Spring profile
- Persists documents to PostgreSQL via Spring Data JPA
- Stores document embeddings as `float[]` using a custom JPA converter (768 dimensions for nomic-embed-text)
- Semantic search uses pgvector's cosine similarity operator (`<=>`)
- Requires PostgreSQL with the pgvector extension

## Project Metadata

- **Group ID**: `com.example.fhir`
- **Artifact**: `document-service`
- **Packaging**: JAR
- **Configuration**: YAML

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| Spring Web | REST API endpoints |
| Spring Data JPA | Database persistence (pgvector mode) |
| H2 Database | In-memory database (default mode) |
| PostgreSQL JDBC | pgvector mode |
| Jackson | JSON serialization |
| Hibernate | JPA implementation |

## Project Structure

```
src/
├── main/java/com/example/fhir/
│   ├── config/
│   │   ├── PgVectorDialect.java         Custom Hibernate dialect for pgvector
│   │   ├── PgVectorSearchConfig.java     Spring config for pgvector profile
│   │   └── VectorConverter.java          JPA converter: float[] ↔ pgvector
│   ├── controller/
│   │   └── DocumentController.java       REST and FHIR endpoints
│   ├── document_service/
│   │   └── DocumentServiceApplication.java  Spring Boot entry point
│   ├── model/
│   │   ├── ClinicalDocument.java         Core document POJO
│   │   ├── DocumentEntity.java           JPA entity (pgvector mode)
│   │   └── DocumentIngestRequest.java    Ingest request record
│   ├── repository/
│   │   └── DocumentRepository.java       Spring Data repository
│   └── service/
│       ├── AiMetadataService.java        AI enrichment client
│       ├── DocumentService.java          Core business logic
│       ├── EmbeddingService.java         Embedding client
│       └── VectorSearchService.java      pgvector search service
└── test/java/com/example/fhir/document_service/
    └── DocumentServiceApplicationTests.java  Integration tests
```

## API Endpoints

### Application JSON API

| Method | Path | Description | Parameters |
|--------|------|-------------|------------|
| `GET` | `/api/document` | List/search documents | `patient`, `type`, `q` |
| `GET` | `/api/document/{id}` | Get document by ID | — |
| `POST` | `/api/document` | Ingest a new document | Request body (JSON) |

### FHIR API

| Method | Path | Description | Parameters |
|--------|------|-------------|------------|
| `GET` | `/fhir/DocumentReference` | Search as FHIR Bundle | `patient`, `type`, `_text` |
| `GET` | `/fhir/DocumentReference/{id}` | Get as FHIR DocumentReference | — |

## FHIR DocumentReference Implementation

The service returns FHIR R4 `DocumentReference` resources with the following elements:

### Required
- `masterIdentifier` — URI-based identifier (`urn:uuid:{id}`)
- `status` — `"current"`
- `docStatus` — `"final"`
- `subject` — `Patient/{patientId}` reference with display name
- `type` — LOINC-coded document type (e.g., `18842-5` for Discharge summary)
- `content[].attachment.contentType` — MIME type
- `content[].attachment.url` — Document URL

### Recommended
- `identifier` — Business identifier with system/value
- `category` — HL7 document classcodes with LOINC list codes
- `date` — ISO 8601 creation timestamp
- `author` — `Practitioner/{sanitized-name}` reference with display
- `custodian` — `Organization/{sanitized-name}` reference with display
- `description` — AI-generated summary
- `content[].attachment.title` — Document title
- `content[].attachment.creation` — Creation timestamp
- `securityLabel` — v3-Confidentiality code (e.g., `NORMAL`)
- `context.encounter` — Reference to associated Encounter
- `context.period` — Document validity period (start/end)
- `context.facilityType` — Facility type (e.g., "Cardiology Department")
- `context.practiceSetting` — Clinical specialty (e.g., "Cardiology")
- `extension` — AI-assisted metadata (keywords)

### Bundle Response
- `resourceType: "Bundle"`
- `id`, `type: "searchset"`
- `timestamp`, `total`
- `link` — Self-link for pagination
- `entry[].fullUrl`, `entry[].resource`, `entry[].search.mode`

## LOINC Code Mapping

### Document Types
| Input | LOINC Code | Display |
|-------|-----------|---------|
| Discharge summary | `18842-5` | Discharge summary |
| Radiology report / Imaging | `18748-4` | Diagnostic imaging report |
| Laboratory report / Lab | `26436-6` | Laboratory report |
| Clinical note | `34108-1` | Clinical note |
| Progress note | `11506-3` | Progress note |
| Consultation | `11488-3` | Consultation note |
| Procedure note | `28570-0` | Procedure note |
| Pathology report | `11526-1` | Pathology report |

### Categories
| Input | LOINC List Code | Display |
|-------|----------------|---------|
| clinical-note | `LP173421-1` | Clinical note |
| imaging | `LP29693-6` | Imaging |
| lab / pathology | `LP7839-6` | Laboratory |
| cardiology | `LP104197-4` | Cardiology |

## AI Integration

The service integrates with the AI sidecar (Python FastAPI) at the URL configured by `ai.sidecar.base-url` (default: `http://127.0.0.1:8090`).

### Enrichment Flow
1. **Document ingest** → POST `/enrich` → returns enhanced title, summary, keywords
2. **Semantic scoring** → POST `/score` → returns relevance score for search results
3. **Embedding generation** (pgvector mode) → POST `/embed` → returns embedding vector

### Fallback Behavior
When the AI sidecar is unavailable, the service falls back to:
- **Title inference**: First 72 characters of content
- **Summary**: First 180 characters of content (or content as-is if shorter)
- **Keywords**: Extracted from a curated list of clinical terms plus token frequency analysis
- **Semantic scoring**: Weighted keyword matching against document fields (title 8×, type 5×, category 4×, patient name 4×, keywords 6×, summary 3×)

## Configuration

### application.yaml
```yaml
spring:
  application:
    name: document-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

ai:
  sidecar:
    base-url: ${AI_SIDECAR_BASE_URL:http://127.0.0.1:8090}
    timeout: ${AI_SIDECAR_TIMEOUT:35s}
```

### Profiles
- **`dev`** (default) — H2 in-memory database
- **`pgvector`** — PostgreSQL with pgvector extension

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |
| `AI_SIDECAR_BASE_URL` | `http://127.0.0.1:8090` | AI sidecar endpoint |
| `AI_SIDECAR_TIMEOUT` | `35s` | Sidecar request timeout |

## Running Locally

```powershell
cd backend/document-service
.\mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

### With pgvector

```powershell
.\mvnw spring-boot:run -Dspring-boot.run.profiles=pgvector
```

## Running Tests

```powershell
.\mvnw test
```

Tests include:
- Context loading verification
- FHIR Bundle response validation (checks `resourceType`, `DocumentReference`, and content)
- Document ingest + AI metadata enrichment (tests fallback behavior when sidecar is unavailable)

## Build

```powershell
.\mvnw clean package
```

Produces `target/document-service-0.0.1-SNAPSHOT.jar`.

## Seed Data

Three sample documents are loaded on startup (in-memory mode only):

| ID | Patient | Type | Description |
|----|---------|------|-------------|
| doc-001 | PAT-1001 (Asha Rao) | Discharge summary | Cardiology discharge |
| doc-002 | PAT-1002 (Rahul Menon) | Radiology report | Chest imaging |
| doc-003 | PAT-1001 (Asha Rao) | Laboratory report | Diabetes lab trend |