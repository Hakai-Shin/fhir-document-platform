import json
import logging
from typing import Optional

import httpx
import numpy as np
from config import settings

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Prompts
# ---------------------------------------------------------------------------

SYSTEM_TITLE = """You are a clinical document processing assistant. Extract the most appropriate clinical title from the document content below. The title should be concise, medically relevant, and no longer than 72 characters. Respond with ONLY the title text — no explanation, no formatting, no extra words."""

SYSTEM_SUMMARY = """You are a clinical summarisation assistant. Summarise the following clinical document content in 1–3 sentences. Focus on the key clinical findings, diagnoses, medications, and recommendations. Be concise and factual. Respond with ONLY the summary text."""

SYSTEM_KEYWORDS = """You are a clinical NLP assistant. Extract 4-8 key clinical terms, conditions, procedures, medications, or findings from the text below. Respond with ONLY a raw JSON array of strings, e.g. ["diabetes", "insulin", "HbA1c", "nephropathy"]. No markdown, no explanation, no formatting."""


# ---------------------------------------------------------------------------
# Ollama helpers
# ---------------------------------------------------------------------------

async def _ollama_generate(
    model: str,
    system: str,
    prompt: str,
    *,
    timeout: int = 30,
    base_url: str = "",
) -> str:
    """Call Ollama's generate endpoint and return the response text."""
    url = f"{base_url.rstrip('/')}/api/generate"
    payload = {
        "model": model,
        "system": system,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.1,       # low temperature for deterministic output
            "num_predict": 512,
        },
    }
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.post(url, json=payload)
        resp.raise_for_status()
        data = resp.json()
    return data.get("response", "").strip()


async def _ollama_embed(
    text: str,
    *,
    model: str = "",
    base_url: str = "",
    timeout: int = 10,
) -> list[float]:
    """Call Ollama's embeddings endpoint and return the embedding vector."""
    url = f"{base_url.rstrip('/')}/api/embeddings"
    payload = {
        "model": model,
        "prompt": text,
    }
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.post(url, json=payload)
        resp.raise_for_status()
        data = resp.json()
    return data.get("embedding", [])


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

async def generate_title(content: str) -> Optional[str]:
    """Use LLM to generate a concise clinical title."""
    if not content or not content.strip():
        return None
    try:
        result = await _ollama_generate(
            settings.llm_model,
            SYSTEM_TITLE,
            content[:settings.max_content_length],
            timeout=settings.llm_timeout,
            base_url=settings.ollama_base_url,
        )
        return result[:72] if result else None
    except Exception:
        logger.warning("LLM title generation failed", exc_info=True)
        return None


async def generate_summary(content: str) -> Optional[str]:
    """Use LLM to generate a clinical summary."""
    if not content or not content.strip():
        return None
    try:
        result = await _ollama_generate(
            settings.llm_model,
            SYSTEM_SUMMARY,
            content[:settings.max_content_length],
            timeout=settings.llm_timeout,
            base_url=settings.ollama_base_url,
        )
        return result if result else None
    except Exception:
        logger.warning("LLM summary generation failed", exc_info=True)
        return None


async def extract_keywords(content: str) -> list[str]:
    """Use LLM to extract clinical keywords as a JSON list."""
    if not content or not content.strip():
        return []
    try:
        result = await _ollama_generate(
            settings.llm_model,
            SYSTEM_KEYWORDS,
            content[:settings.max_content_length],
            timeout=settings.llm_timeout,
            base_url=settings.ollama_base_url,
        )
        # Attempt to parse JSON array from the response
        if result:
            # Handle cases where the model wraps in markdown code fences
            cleaned = result.strip()
            if cleaned.startswith("```"):
                lines = cleaned.splitlines()
                cleaned = "\n".join(
                    line for line in lines if not line.strip().startswith("```")
                )
            try:
                parsed = json.loads(cleaned)
                if isinstance(parsed, list):
                    return [str(k).strip() for k in parsed if k]
            except json.JSONDecodeError:
                logger.warning("Could not parse keywords JSON: %s", result[:200])
        return []
    except Exception:
        logger.warning("LLM keyword extraction failed", exc_info=True)
        return []


async def get_embedding(text: str) -> list[float]:
    """Get embedding vector for the given text."""
    if not text or not text.strip():
        return []
    try:
        return await _ollama_embed(
            text,
            model=settings.embed_model,
            base_url=settings.ollama_base_url,
            timeout=settings.embed_timeout,
        )
    except Exception:
        logger.warning("Embedding generation failed", exc_info=True)
        return []


async def compute_similarity(query: str, document_text: str) -> float:
    """Compute cosine similarity between query and document via embeddings."""
    if not query.strip() or not document_text.strip():
        return 0.0

    query_emb = await get_embedding(query)
    doc_emb = await get_embedding(document_text[:settings.max_content_length])

    if not query_emb or not doc_emb:
        return 0.0

    a = np.array(query_emb, dtype=np.float64)
    b = np.array(doc_emb, dtype=np.float64)

    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)

    if norm_a == 0 or norm_b == 0:
        return 0.0

    return float(np.dot(a, b) / (norm_a * norm_b))