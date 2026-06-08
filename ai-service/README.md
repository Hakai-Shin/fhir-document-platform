# AI Sidecar Service

This folder contains the Python-based AI sidecar for the FHIR document platform.

The sidecar uses a **local LLM** via [Ollama](https://ollama.ai) to provide intelligent clinical document processing:

- **Title inference** — generates a concise, clinically-relevant title from document content
- **Summarisation** — produces a 1-3 sentence clinical summary
- **Keyword extraction** — extracts 4-8 key clinical terms using a local language model
- **Semantic scoring** — scores document relevance for semantic search using embedding-based cosine similarity
- **Embedding generation** — produces vector embeddings for caching or external use

> **Note:** This version replaces the original rule-based logic with actual LLM inference. It requires **Ollama** running locally or in a sibling container.

---

## Architecture

```
┌──────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│  Backend      │────▶│  AI Sidecar          │────▶│  Ollama Server   │
│  (Spring)     │◀────│  FastAPI + httpx     │◀────│  :11434          │
│  :8080        │     │  :8090               │     │  (phi-3.5-mini   │
└──────────────┘     └──────────────────────┘     │   + embed model) │
                                                   └─────────────────┘
```

---

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/enrich` | Generate title, summary, and keywords from content |
| `POST` | `/score` | Score document relevance against a query (cosine similarity) |
| `POST` | `/embed` | Generate an embedding vector for text |

### `POST /enrich`

**Request:**
```json
{
  "title": "optional existing title",
  "content": "Patient presents with type 2 diabetes and hypertension..."
}
```

**Response:**
```json
{
  "title": "Diabetes & Hypertension Management in Type 2 Patient",
  "summary": "Patient with type 2 diabetes and hypertension on Metformin and Lisinopril. HbA1c 7.2%, BP 135/85. Follow-up with endocrinology recommended in 3 months.",
  "keywords": ["diabetes", "hypertension", "metformin", "lisinopril", "endocrinology", "HbA1c"]
}
```

### `POST /score`

**Request:**
```json
{
  "document": {
    "title": "Diabetes Report",
    "content": "...",
    "documentType": "progress-note",
    "category": "endocrinology",
    "patientName": "John Doe",
    "aiSummary": "...",
    "aiKeywords": ["diabetes", "insulin"]
  },
  "query": "diabetes"
}
```

**Response:**
```json
{
  "score": 0.873
}
```

Returns a float from `0.0` (no match) to `1.0` (perfect match) based on embedding cosine similarity.

### `POST /embed`

**Request:**
```json
{
  "text": "Clinical text to embed"
}
```

**Response:**
```json
{
  "embedding": [0.012, -0.034, 0.087, ...]
}
```

---

## Quick Start (Docker Compose — Recommended)

```powershell
# Build and start both Ollama and the AI sidecar
docker compose up --build

# The sidecar will be available at:
# http://localhost:8090
# Ollama API at:
# http://localhost:11434
```

On first startup, Ollama will automatically pull the required models (`phi-3.5-mini:latest` and `nomic-embed-text:latest`). This may take a few minutes depending on your internet connection.

> **GPU Note:** The compose file includes GPU reservation for NVIDIA GPUs. For AMD GPUs or CPU-only mode, remove the `deploy.resources.reservations.devices` section from the `ollama` service.

---

## Run Locally (Without Docker)

### 1. Install Ollama

Download from [ollama.ai](https://ollama.ai) and start the service.

### 2. Pull the required models

```powershell
ollama pull phi-3.5-mini:latest
ollama pull nomic-embed-text:latest
```

### 3. Install Python dependencies

```powershell
cd ai-service
pip install -r requirements.txt
```

### 4. Run the service

```powershell
python main.py
```

Or with custom settings:

```powershell
$env:AI_OLLAMA_BASE_URL = "http://localhost:11434"
$env:AI_LLM_MODEL = "phi-3.5-mini:latest"
$env:AI_EMBED_MODEL = "nomic-embed-text:latest"
python main.py
```

The service will listen on `http://127.0.0.1:8090`.

---

## Configuration

All configuration is via environment variables with the `AI_` prefix:

| Variable | Default | Description |
|---|---|---|
| `AI_HOST` | `0.0.0.0` | Host to bind the HTTP server |
| `AI_PORT` | `8090` | Port to listen on |
| `AI_OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `AI_LLM_MODEL` | `phi-3.5-mini:latest` | Model for text generation (title, summary, keywords) |
| `AI_EMBED_MODEL` | `nomic-embed-text:latest` | Model for embeddings |
| `AI_LLM_TIMEOUT` | `30` | Timeout in seconds for LLM calls |
| `AI_EMBED_TIMEOUT` | `10` | Timeout in seconds for embedding calls |
| `AI_MAX_CONTENT_LENGTH` | `50000` | Max characters to send to LLM (chars) |

---

## GPU Requirements

- **Recommended:** NVIDIA GPU with 6+ GB VRAM
- **Models used:**
  - `phi-3.5-mini:latest` (~3.5-4 GB VRAM) — text generation
  - `nomic-embed-text:latest` (~0.5 GB VRAM) — embeddings
- **Total VRAM needed:** ~4-5 GB

### CPU Fallback

If no GPU is available, you can use a smaller model:

```powershell
$env:AI_LLM_MODEL = "gemma2:2b"
```

Or set `OLLAMA_USE_CUDA=0` to force CPU mode (slower but functional).

---

## Testing

```powershell
cd ai-service
pip install pytest pytest-asyncio httpx
pytest tests/ -v
```

Tests mock the Ollama calls, so no GPU or running service is needed.

---

## API Documentation

When the service is running, interactive API docs are available at:

- Swagger UI: `http://localhost:8090/docs`
- ReDoc: `http://localhost:8090/redoc`

---

## Environment Variables for the Backend

The Spring Boot backend expects the sidecar at a configurable URL:

```yaml
# application.yml
ai:
  sidecar:
    base-url: http://localhost:8090    # or http://ai-service:8090 in Docker
    timeout: 10s                       # increased from default 2s for LLM calls