# FHIR Document Platform

FHIR Document Platform is a clinical document discovery system that organizes healthcare documents around FHIR `DocumentReference` resources.
FHIR, short for Fast Healthcare Interoperability Resources, is the HL7 standard for exchanging healthcare data in a consistent, machine-readable way.
Instead of treating documents as loose files or custom JSON, this project wraps them in a FHIR-friendly API so they can be searched, shared, and integrated more easily across healthcare software.

The platform combines a Spring Boot backend, a Python AI sidecar (FastAPI), and a React document explorer to make clinical documents easier to find and review.
The backend exposes deterministic FHIR DocumentReference and REST endpoints, the AI sidecar enriches metadata with titles, summaries, keywords, and embedding-based semantic scoring, and the UI gives clinicians or support staff a simple way to browse document metadata and inspect the resulting FHIR payloads.

The main benefit is better document retrieval without losing standards compliance.
Clinical documents become easier to index, search, and review, while the FHIR API stays predictable, auditable, and suitable for system-to-system integration.

## What The Platform Does

- Exposes documents as FHIR R4 `DocumentReference` resources with full context (encounter, period, facility type, practice setting, security labels)
- Provides simple REST endpoints for listing, reading, and ingesting documents
- Uses an AI-assisted metadata layer (Ollama-based) to derive titles, summaries, keywords, and embedding vectors
- Supports semantic search over seeded and ingested clinical documents via local scoring or pgvector cosine similarity
- Includes a React-based document explorer for search, browsing, and inspection of document metadata and FHIR payloads

## High-Level Architecture

The platform is split into three parts:

1. **Backend document service** (`backend/document-service/`)
   - Spring Boot 3.5.9 application (Java 17)
   - In-memory store by default; PostgreSQL + pgvector support with the `pgvector` profile
   - Returns both application-friendly JSON and FHIR R4 DocumentReference resources
   - Full LOINC coding for document types and categories

2. **AI metadata sidecar** (`ai-service/`)
   - Python FastAPI service
   - Connects to Ollama for LLM-based title/summary/keyword generation and embeddings
   - Models: `phi3.5:latest` (LLM) and `nomic-embed-text:latest` (embedding)
   - Falls back to deterministic rules when Ollama is unavailable
   - Tested with pytest + mocked LLM responses

3. **Frontend explorer** (`frontend/`)
   - React 19 + Vite 7 application
   - Searches documents through the backend API
   - Displays metadata and the generated FHIR DocumentReference payload
   - Uses Lucide React icons

## Technology Used

### Backend

- Java 17
- Spring Boot 3.5.9
- Spring Web, Spring Data JPA
- H2 database (default local mode)
- PostgreSQL + pgvector extension (with `pgvector` Spring profile)
- Maven Wrapper

### AI Sidecar

- Python 3.11+
- FastAPI + Uvicorn
- Ollama (local LLM inference)
- HTTPX (async HTTP client)
- NumPy (cosine similarity computation)
- Pydantic / Pydantic-Settings
- Docker + Docker Compose

### Frontend

- React 19
- Vite 7
- Lucide React icons

### Supporting Tools

- npm for frontend dependency management
- Maven Wrapper for backend builds
- Ollama for local LLM inference

## Repository Layout

```text
.
├── README.md                  Project overview and this file
├── ai-service/                Python FastAPI AI sidecar
│   ├── config.py              Settings (model names, URLs, timeouts)
│   ├── docker-compose.yml     Ollama + AI sidecar orchestration
│   ├── Dockerfile             AI sidecar container build
│   ├── llm_client.py          Ollama API client (generate, embed)
│   ├── main.py                FastAPI application entrypoint
│   ├── requirements.txt       Python dependencies
│   ├── pytest.ini             Pytest configuration
│   └── tests/                 Test suite
├── backend/
│   └── document-service/      Spring Boot backend
│       ├── pom.xml            Maven project descriptor
│       └── src/               Application source and tests
├── docs/                      Project documentation
│   └── adr/                   Architecture Decision Records
└── frontend/                  React document explorer
    ├── index.html             Vite entry HTML
    ├── package.json           npm dependencies
    ├── vite.config.js         Vite configuration
    └── src/                   React application source
```

## Architecture Decision Records

The project's key technical decisions are documented in [`docs/adr/`](docs/adr/):

- [ADR-001](docs/adr/ADR-001-fhir-facade-vs-full-server.md) — FHIR Facade vs. Full FHIR Server
- [ADR-002](docs/adr/ADR-002-ai-sidecar-pattern.md) — AI Sidecar Pattern vs. Embedded AI
- [ADR-003](docs/adr/ADR-003-in-memory-vs-pgvector.md) — In-Memory Store vs. PostgreSQL with pgvector
- [ADR-004](docs/adr/ADR-004-model-selection.md) — Model Selection for LLM and Embeddings
- [ADR-005](docs/adr/ADR-005-loinc-mapping.md) — LOINC Code Mapping Strategy

## FHIR DocumentReference Implementation

The project implements a FHIR R4 `DocumentReference` resource with the following elements:

### Required
- ✅ `masterIdentifier` — URI-based unique identifier across systems
- ✅ `status` — `current`
- ✅ `docStatus` — `final`
- ✅ `subject` — Reference to `Patient/{id}` with display name
- ✅ `type` — LOINC-coded document type (e.g., `18842-5` for Discharge summary)
- ✅ `content[].attachment.contentType` — MIME type
- ✅ `content[].attachment.url` — Document URL

### Recommended
- ✅ `identifier` — Business identifiers
- ✅ `category` — HL7 document classcodes with LOINC mapping
- ✅ `date` — ISO 8601 timestamp
- ✅ `author` — Reference to `Practitioner/{id}` with display
- ✅ `custodian` — Reference to `Organization/{id}` with display
- ✅ `description` — AI-generated summary
- ✅ `content[].attachment.title` — Document title
- ✅ `content[].attachment.creation` — Creation timestamp
- ✅ `securityLabel` — v3-Confidentiality codes
- ✅ `context.encounter` — Reference to the associated Encounter
- ✅ `context.period` — Document validity/activity period
- ✅ `context.facilityType` — Facility where document was created
- ✅ `context.practiceSetting` — Clinical specialty

### Bundle Response
- ✅ `resourceType: "Bundle"`
- ✅ `id`, `type: "searchset"`
- ✅ `timestamp`, `total`, `link` (pagination)
- ✅ `entry[]` with `fullUrl`, `resource`, `search.mode`

## API Summary

### Application JSON API

- `GET /api/document`
  - Returns the available documents
  - Optional filters: `patient`, `type`, `q`

- `GET /api/document/{id}`
  - Returns a single document by ID

- `POST /api/document`
  - Ingests a new document
  - The AI sidecar enriches the submitted data before storing it
  - Optional FHIR context fields: `encounterId`, `encounterDisplay`, `periodStart`, `periodEnd`, `facilityType`, `practiceSetting`, `securityLabel`

### FHIR API

- `GET /fhir/DocumentReference`
  - Returns a FHIR `Bundle` containing `DocumentReference` resources
  - Optional filters: `patient`, `type`, `_text`

- `GET /fhir/DocumentReference/{id}`
  - Returns a single FHIR `DocumentReference`

## Prerequisites

Before running the project locally, ensure you have:

- Java 17+ and Maven (or use the Maven Wrapper)
- Node.js 18+ and npm
- Python 3.11+
- Ollama installed with required models

### Installing Ollama

1. Download and install Ollama from [ollama.com](https://ollama.com/download)
2. Pull the required models:

```powershell
ollama pull phi3.5:latest
ollama pull nomic-embed-text:latest
```

3. Verify models are available:

```powershell
ollama list
```

Expected output:
```
NAME                       ID              SIZE      MODIFIED
phi3.5:latest              61819fb370a3    2.2 GB    ...
nomic-embed-text:latest    0a109f422b47    274 MB    ...
```

## Local Development

### AI Sidecar

From `ai-service`:

```powershell
python main.py
```

Sidecar URL:

```text
http://127.0.0.1:8090
```

The sidecar requires Ollama to be running on `http://localhost:11434` (Ollama's default).

### Backend

From `backend/document-service`:

```powershell
.\mvnw spring-boot:run
```

Backend URL:

```text
http://localhost:8080
```

The backend talks to the AI sidecar at `http://127.0.0.1:8090` by default.
If the sidecar is unavailable, the backend falls back to its local enrichment logic so the API still works.

**pgvector profile** (requires PostgreSQL with pgvector extension):

```powershell
.\mvnw spring-boot:run -Dspring-boot.run.profiles=pgvector
```

### Frontend

From `frontend`:

```powershell
npm install
npm run dev
```

Frontend URL:

```text
http://127.0.0.1:5173
```

The frontend points to the backend at `http://localhost:8080` by default.
If your backend is running elsewhere, set:

```powershell
$env:VITE_API_BASE_URL="http://your-backend-host:8080"
```

## How To Deploy The Backend

Build the Spring Boot jar:

```powershell
cd backend/document-service
.\mvnw clean package
```

This creates:

```text
target/document-service-0.0.1-SNAPSHOT.jar
```

Run the backend:

```powershell
java -jar target\document-service-0.0.1-SNAPSHOT.jar
```

To run on a different port:

```powershell
java -jar target\document-service-0.0.1-SNAPSHOT.jar --server.port=8081
```

For server deployment, copy the jar to the target machine and run it with Java 17 or newer.
You can put Nginx, Apache, or a load balancer in front of it if you want a public endpoint.

Before starting the backend in production, make sure the AI sidecar is also running or point `AI_SIDECAR_BASE_URL` at its host.

## How To Deploy The Frontend

Build the production frontend:

```powershell
cd frontend
npm install
npm run build
```

The static production output is written to:

```text
frontend/dist
```

You can preview the built frontend locally with:

```powershell
npm run preview
```

The frontend can be hosted on any static file host, such as:

- Nginx
- Apache
- Azure Static Web Apps
- Netlify
- Vercel
- S3 or another object store with static hosting

Make sure the hosted frontend is configured to talk to the deployed backend API URL.

## How To Deploy The AI Sidecar

Run it directly:

```powershell
cd ai-service
python main.py
```

Or build and run the Docker image (with Docker Compose):

```powershell
cd ai-service
docker compose up --build
```

This starts both the Ollama service and the AI sidecar container.
The backend expects the sidecar at `http://127.0.0.1:8090` unless `AI_SIDECAR_BASE_URL` is set.

## Running Tests

### Backend Tests

```powershell
cd backend/document-service
.\mvnw test
```

### AI Sidecar Tests

```powershell
cd ai-service
pip install -r requirements.txt
pytest -v
```

## End-to-End Deployment Flow

1. Install Ollama and pull the required models (`phi3.5:latest`, `nomic-embed-text:latest`)
2. Start the AI sidecar (directly or via Docker Compose)
3. Deploy the backend jar and confirm `http://your-backend-host:8080/api/document` works
4. Build the frontend with `npm run build`
5. Upload the `frontend/dist` folder to your static host
6. Configure the frontend API base URL to point at the deployed backend
7. Open the frontend and search or ingest documents through the UI

## Example Workflow

1. Open the frontend explorer.
2. Search by patient, type, or semantic text.
3. Select a document to inspect the metadata panel.
4. Review the generated FHIR `DocumentReference` JSON (includes full context with encounter, period, security labels, LOINC-coded types).
5. Ingest a new document and see the AI-assisted title, summary, and keywords appear immediately.

## Notes

- The default data store is in-memory, so documents reset when the backend restarts.
- For persistent storage, activate the `pgvector` Spring profile with a PostgreSQL + pgvector database.
- The AI layer is deliberately deterministic and lightweight for auditability.
- The backend is CORS-enabled for local frontend development on ports `5173` and `127.0.0.1:5173`.