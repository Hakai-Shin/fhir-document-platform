# ADR-002: AI Sidecar Pattern vs. Embedded AI

**Status**: Accepted
**Date**: 2026-06-09
**Deciders**: Project Lead

---

## Context

The platform needs AI capabilities for document enrichment (title, summary, keywords) and semantic search (embedding generation, relevance scoring). Two integration patterns were considered:

1. **Embedded AI**: Run LLM calls directly within the Spring Boot backend
2. **Sidecar AI**: Run AI logic in a separate Python service that the backend calls via HTTP

The sidecar is implemented as a FastAPI application in `ai-service/` that communicates with Ollama for local LLM inference.

## Decision

We chose the **AI sidecar pattern** — a separate Python FastAPI service that the Spring Boot backend calls via REST endpoints (`/enrich`, `/score`, `/embed`).

## Consequences

**Positive**:
- Language-appropriate tooling: Python has superior NLP/ML ecosystem (NumPy, scikit-learn, sentence-transformers)
- Independent scaling: AI service can scale separately from the backend
- Fault isolation: AI failures don't crash the backend — it falls back gracefully (`AiMetadataService` catches `RestClientException`)
- Technology flexibility: Can swap Ollama for OpenAI, Anthropic, etc. without changing the backend
- Clear API boundary: Sidecar interface is only 3 endpoints — easy to test, mock, and document
- Development velocity: Python devs can work on AI independently of Java devs

**Negative**:
- Network latency: Each enrichment/scoring call adds HTTP round-trip overhead
- Deployment complexity: Two services to deploy and monitor instead of one
- Serialization overhead: Java ↔ JSON ↔ Python, especially for embedding vectors
- Dual-language maintenance: Requires proficiency in both Java and Python

## Alternatives Considered

### Embedded Java AI (LangChain4j, Spring AI)
- Run LLM calls directly from the Spring Boot process
- **Rejected**: Java LLM ecosystem is less mature; embedding math in Java is verbose; would need TensorFlow Java or ONNX Runtime

### Embedded Python via Sidecar Process
- Launch a Python subprocess from Java and communicate via stdin/stdout
- **Rejected**: Process management complexity, no health checks, harder to debug

## Graceful Degradation

The backend (`AiMetadataService`) has three tiers of AI capability:
1. **Sidecar available** — Full AI enrichment and semantic scoring
2. **Sidecar unavailable** — Local deterministic rules (keyword matching, text truncation)
3. **Fallback** — Title defaults to "Untitled clinical document", summary to "No document body provided."

This ensures the API never 500s due to AI infrastructure issues.

## Related
- ADR-001: FHIR Facade vs. Full FHIR Server
- ADR-004: Model Selection for LLM and Embeddings