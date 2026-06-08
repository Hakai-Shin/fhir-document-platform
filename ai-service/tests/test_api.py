"""Tests for the AI sidecar REST API."""

import pytest
from httpx import AsyncClient


class TestHealth:
    """GET /health"""

    async def test_health_endpoint(self, client: AsyncClient):
        resp = await client.get("/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "ok"
        assert data["service"] == "ai-sidecar"
        assert "model" in data


class TestEnrich:
    """POST /enrich"""

    async def test_enrich_with_content(self, client: AsyncClient, mock_ollama):
        payload = {"content": "Patient has diabetes and hypertension."}
        resp = await client.post("/enrich", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "Mocked Clinical Title"
        assert data["summary"] == "Mocked clinical summary of the document."
        assert "diabetes" in data["keywords"]

    async def test_enrich_without_content(self, client: AsyncClient):
        resp = await client.post("/enrich", json={"title": "My Document"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "My Document"
        assert data["summary"] == "No document body provided."
        assert data["keywords"] == []

    async def test_enrich_empty_payload(self, client: AsyncClient):
        resp = await client.post("/enrich", json={})
        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "Untitled clinical document"

    async def test_enrich_llm_fallback_to_input_title(self, client: AsyncClient, mock_ollama):
        """When LLM returns None, fall back to provided title."""
        mock_ollama["title"].return_value = None
        payload = {"title": "Fallback Title", "content": "Some clinical text here."}
        resp = await client.post("/enrich", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "Fallback Title"

    async def test_enrich_llm_all_fail(self, client: AsyncClient, mock_ollama):
        """When all LLM calls fail, return appropriate defaults."""
        mock_ollama["title"].return_value = None
        mock_ollama["summary"].return_value = None
        mock_ollama["keywords"].return_value = []
        payload = {"content": "Some clinical text here."}
        resp = await client.post("/enrich", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "Untitled clinical document"
        assert data["summary"] == "Summary could not be generated."
        assert data["keywords"] == []


class TestScore:
    """POST /score"""

    async def test_score_with_query(self, client: AsyncClient, mock_ollama):
        payload = {
            "document": {
                "title": "Diabetes Report",
                "content": "Patient has type 2 diabetes.",
                "documentType": "progress-note",
                "category": "endocrinology",
                "patientName": "John Doe",
                "aiSummary": "Diabetes management note.",
                "aiKeywords": ["diabetes", "insulin"],
            },
            "query": "diabetes",
        }
        resp = await client.post("/score", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["score"] == 0.85

    async def test_score_empty_query(self, client: AsyncClient):
        payload = {"document": {"title": "Test"}, "query": ""}
        resp = await client.post("/score", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["score"] == 0.0

    async def test_score_no_query(self, client: AsyncClient):
        payload = {"document": {"title": "Test"}}
        resp = await client.post("/score", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["score"] == 0.0


class TestEmbed:
    """POST /embed"""

    async def test_embed_text(self, client: AsyncClient):
        # This test uses the real code path. Mock the underlying call.
        import main as app_module
        from unittest.mock import patch

        mock_embedding = [0.1, 0.2, 0.3, 0.4, 0.5]
        with patch("main.get_embedding", return_value=mock_embedding):
            resp = await client.post("/embed", json={"text": "Clinical text for embedding"})
            assert resp.status_code == 200
            data = resp.json()
            assert data["embedding"] == mock_embedding

    async def test_embed_empty_text(self, client: AsyncClient):
        resp = await client.post("/embed", json={"text": ""})
        assert resp.status_code == 422

    async def test_embed_missing_text(self, client: AsyncClient):
        resp = await client.post("/embed", json={})
        assert resp.status_code == 422

    async def test_embed_service_unavailable(self, client: AsyncClient):
        from unittest.mock import patch
        with patch("main.get_embedding", return_value=[]):
            resp = await client.post("/embed", json={"text": "Some text"})
            assert resp.status_code == 503


class TestNotFound:
    """Catch-all for unknown endpoints."""

    async def test_unknown_route(self, client: AsyncClient):
        resp = await client.get("/unknown")
        assert resp.status_code == 404

    async def test_unknown_post_route(self, client: AsyncClient):
        resp = await client.post("/unknown", json={})
        assert resp.status_code == 404