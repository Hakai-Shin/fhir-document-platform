"""Test fixtures for AI sidecar tests."""

from collections.abc import AsyncGenerator
from unittest.mock import patch

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient


@pytest.fixture
def app() -> FastAPI:
    """Import the FastAPI app for testing."""
    from main import app
    return app


@pytest.fixture
async def client(app: FastAPI) -> AsyncGenerator[AsyncClient, None]:
    """Create an async test client for the FastAPI app."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture
def mock_ollama():
    """Mock the LLM client functions to return controlled responses."""
    with patch("main.generate_title") as mock_title, \
         patch("main.generate_summary") as mock_summary, \
         patch("main.extract_keywords") as mock_keywords, \
         patch("main.compute_similarity") as mock_similarity:

        mock_title.return_value = "Mocked Clinical Title"
        mock_summary.return_value = "Mocked clinical summary of the document."
        mock_keywords.return_value = ["diabetes", "hypertension", "medication"]
        mock_similarity.return_value = 0.85

        yield {
            "title": mock_title,
            "summary": mock_summary,
            "keywords": mock_keywords,
            "similarity": mock_similarity,
        }


# Sample clinical document for testing
SAMPLE_CLINICAL_TEXT = """
Patient presents with type 2 diabetes mellitus and hypertension.
Current medications include Metformin 500mg twice daily and Lisinopril 10mg daily.
HbA1c is 7.2%, blood pressure 135/85. Recommended follow-up in 3 months
with endocrinology for diabetes management. Lab results show creatinine
1.1 mg/dL and hemoglobin 13.5 g/dL. No evidence of diabetic retinopathy
on recent eye examination.
"""