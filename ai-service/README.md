# AI Sidecar Service

This folder contains the Python-based AI sidecar for the FHIR document platform.

The sidecar is intentionally lightweight and deterministic. It is responsible for:

- deriving a better title when one is not provided
- generating a short summary
- extracting clinical keywords
- scoring document relevance for semantic search

The Spring Boot backend calls this service over HTTP.

## Endpoints

- `GET /health`
- `POST /enrich`
- `POST /score`

## Run Locally

This service only uses the Python standard library.

```powershell
cd ai-service
python main.py
```

It listens on:

```text
http://127.0.0.1:8090
```

## Environment

The backend expects the sidecar at:

```text
http://127.0.0.1:8090
```

You can override this by setting:

```powershell
$env:AI_SIDECAR_BASE_URL="http://your-sidecar-host:8090"
```

## Docker

Build and run:

```powershell
docker build -t fhir-ai-sidecar .
docker run -p 8090:8090 fhir-ai-sidecar
```

