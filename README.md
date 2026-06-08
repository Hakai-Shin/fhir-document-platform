# FHIR Document Platform

FHIR Document Platform is a lightweight clinical document discovery system built around FHIR `DocumentReference` resources.
It exposes healthcare documents through a standards-aligned API, adds an AI-assisted metadata sidecar for indexing and semantic search, and ships with a small React UI for browsing and inspecting documents.

The main goal of the project is to make clinical documents easier to find and review without making the FHIR API itself unpredictable.
The AI layer is intentionally assistive rather than authoritative: it enriches metadata, extracts keywords, generates summaries, and improves search ranking, while the API responses remain deterministic and auditable.

## What The Platform Does

- Exposes documents as FHIR `DocumentReference` resources
- Provides a simple REST API for listing, reading, and ingesting documents
- Uses an AI-assisted metadata layer to derive summaries and keywords
- Supports semantic search over seeded and ingested clinical documents
- Includes a React-based document explorer for search, browsing, and inspection

## High-Level Architecture

The platform is split into three parts:

1. Backend document service
   - Spring Boot application
   - Stores documents in memory for this project version
   - Returns application-friendly JSON and FHIR-shaped responses

2. AI metadata sidecar
   - Python HTTP service in `ai-service/`
   - Generates derived metadata such as titles, summaries, and keywords
   - Scores search relevance using simple deterministic rules
   - Keeps the core API flow deterministic and easy to audit

3. Frontend explorer
   - React + Vite application
   - Searches documents through the backend API
   - Displays metadata and the generated FHIR `DocumentReference` payload

## Technology Used

### Backend

- Java 17
- Spring Boot 3.5.9
- Spring Web
- Spring Data JPA
- H2 database dependency for local/runtime compatibility
- Maven

### AI Sidecar

- Python standard library
- Built-in `http.server`
- JSON over HTTP
- Docker-friendly runtime

### Frontend

- React 19
- Vite 7
- Lucide React icons

### Supporting Tools

- npm for frontend dependency management
- Maven Wrapper for backend builds

## Repository Layout

```text
backend/document-service   Spring Boot backend service
frontend                   React document explorer
README.md                  Project overview and deployment instructions
```

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

### FHIR API

- `GET /fhir/DocumentReference`
  - Returns a FHIR `Bundle` containing `DocumentReference` resources
  - Optional filters: `patient`, `type`, `_text`

- `GET /fhir/DocumentReference/{id}`
  - Returns one FHIR `DocumentReference`

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

### How To Deploy The AI Sidecar

Run it directly:

```powershell
cd ai-service
python main.py
```

Or build and run the Docker image:

```powershell
cd ai-service
docker build -t fhir-ai-sidecar .
docker run -p 8090:8090 fhir-ai-sidecar
```

The backend expects the sidecar at `http://127.0.0.1:8090` unless `AI_SIDECAR_BASE_URL` is set.

## End-to-End Deployment Flow

1. Deploy the backend jar and confirm `http://your-backend-host:8080/api/document` works.
2. Build the frontend with `npm run build`.
3. Upload the `frontend/dist` folder to your static host.
4. Configure the frontend API base URL to point at the deployed backend.
5. Open the frontend and search or ingest documents through the UI.

## Example Workflow

1. Open the frontend explorer.
2. Search by patient, type, or semantic text.
3. Select a document to inspect the metadata panel.
4. Review the generated FHIR `DocumentReference` JSON.
5. Ingest a new document and see the AI-assisted summary and keywords appear immediately.

## Notes

- The current data store is in-memory, so documents reset when the backend restarts.
- The AI layer is deliberately deterministic and lightweight for auditability.
- The backend is CORS-enabled for local frontend development on ports `5173` and `127.0.0.1:5173`.
