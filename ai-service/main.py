import logging
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from config import settings
from llm_client import (
    compute_similarity,
    extract_keywords,
    generate_summary,
    generate_title,
    get_embedding,
)

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Request / Response models
# ---------------------------------------------------------------------------

class EnrichRequest(BaseModel):
    title: str | None = None
    content: str | None = None

    model_config = {"extra": "ignore"}


class EnrichResponse(BaseModel):
    title: str
    summary: str
    keywords: list[str]


class ScoreRequest(BaseModel):
    document: dict = Field(default_factory=dict)
    query: str | None = None

    model_config = {"extra": "ignore"}


class ScoreResponse(BaseModel):
    score: float


class EmbedRequest(BaseModel):
    text: str

    model_config = {"extra": "ignore"}


class EmbedResponse(BaseModel):
    embedding: list[float]


# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(_app: FastAPI):
    logger.info("AI service starting (model=%s, embed=%s)", settings.llm_model, settings.embed_model)
    yield
    logger.info("AI service shutting down")


app = FastAPI(
    title="FHIR AI Sidecar",
    version="2.0.0",
    lifespan=lifespan,
)


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok", "service": "ai-sidecar", "model": settings.llm_model}


@app.post("/enrich", response_model=EnrichResponse)
async def enrich(payload: EnrichRequest, request: Request):
    """Generate AI title, summary, and keywords from document content."""
    content = (payload.content or "").strip()

    if not content:
        return EnrichResponse(
            title=payload.title or "Untitled clinical document",
            summary="No document body provided.",
            keywords=[],
        )

    # Run LLM calls concurrently
    import asyncio

    ai_title, ai_summary, ai_keywords = await asyncio.gather(
        generate_title(content),
        generate_summary(content),
        extract_keywords(content),
    )

    title = ai_title or payload.title or "Untitled clinical document"
    summary = ai_summary or "Summary could not be generated."
    keywords = ai_keywords if ai_keywords else []

    logger.info("enrich title_len=%d summary_len=%d keywords=%d", len(title), len(summary), len(keywords))

    return EnrichResponse(title=title, summary=summary, keywords=keywords)


@app.post("/score", response_model=ScoreResponse)
async def score(payload: ScoreRequest, request: Request):
    """Score document relevance against a query using embedding-based semantic similarity."""
    query = (payload.query or "").strip()
    doc = payload.document or {}

    if not query:
        return ScoreResponse(score=0.0)

    # Build a combined text from the document fields for embedding comparison
    doc_text = " ".join(
        str(v) for v in [
            doc.get("title", ""),
            doc.get("documentType", ""),
            doc.get("category", ""),
            doc.get("patientName", ""),
            doc.get("aiSummary", ""),
            " ".join(doc.get("aiKeywords", [])),
            doc.get("content", ""),
        ] if v
    ).strip()

    similarity = await compute_similarity(query, doc_text)
    logger.info("score query=%s similarity=%.4f", query[:50], similarity)

    return ScoreResponse(score=similarity)


@app.post("/embed", response_model=EmbedResponse)
async def embed(payload: EmbedRequest, request: Request):
    """Generate an embedding vector for the given text. Useful for caching."""
    text = (payload.text or "").strip()
    if not text:
        raise HTTPException(status_code=422, detail="text must not be empty")

    embedding = await get_embedding(text)
    if not embedding:
        raise HTTPException(status_code=503, detail="Embedding service unavailable")

    return EmbedResponse(embedding=embedding)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    import uvicorn

    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        log_level="info",
    )


if __name__ == "__main__":
    main()