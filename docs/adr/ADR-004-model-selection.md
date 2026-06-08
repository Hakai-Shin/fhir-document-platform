# ADR-004: Model Selection for LLM and Embeddings

**Status**: Accepted
**Date**: 2026-06-09
**Deciders**: Project Lead

---

## Context

The AI sidecar requires two types of models:
1. **LLM** — for generating titles, summaries, and keyword extraction
2. **Embedding model** — for semantic search via vector similarity

The platform runs entirely on local infrastructure via Ollama, so models must be Ollama-compatible and runnable on consumer hardware.

## Decision

We selected:
- **LLM**: `phi3.5:latest` (Microsoft Phi 3.5, ~2.2 GB)
- **Embedding**: `nomic-embed-text:latest` (Nomic AI, 768 dimensions, ~274 MB)

Configuration is in `ai-service/config.py` with environment variable overrides:
- `AI_LLM_MODEL` (default: `phi3.5:latest`)
- `AI_EMBED_MODEL` (default: `nomic-embed-text:latest`)

## Rationale

### phi3.5 for LLM
- **Size**: 2.2 GB — fits in 8 GB RAM, no GPU required
- **Quality**: Phi 3.5 mini (3.8B parameters) outperforms models twice its size on reasoning tasks
- **License**: MIT — commercially usable
- **Latency**: 1-3 seconds for typical clinical text (vs 10-30s for 70B models)
- **Context window**: 128K tokens — sufficient for clinical documents
- **Instruction following**: Trained with instruction tuning — produces clean, concise output for our prompts

### nomic-embed-text for Embeddings
- **Dimensions**: 768 — good balance between expressiveness and storage size
- **MTEB score**: Competitive with much larger models on retrieval benchmarks
- **Unrestricted context**: 8192 tokens input
- **License**: Apache 2.0
- **Compatible with pgvector**: Standard 768-dim float array

## Alternatives Considered

### LLM Alternatives
- **Llama 3.1 8B Instruct** — Better quality but 4.7 GB (too large for some dev machines)
- **Mistral 7B** — Similar quality, 4.1 GB
- **gemma:2b** — Faster but lower quality on medical text
- **GPT-4o-mini (OpenAI API)** — Excellent quality but adds API cost, latency, data exfiltration concerns
- **Claude 3.5 Haiku (Anthropic API)** — Same API concerns as OpenAI

### Embedding Alternatives
- **mxbai-embed-large** — 1024 dims, larger model
- **all-minilm** — Only 384 dims, lower quality
- **text-embedding-3-small (OpenAI)** — API-based, dimensions configurable
- **bge-large-en-v1.5** — 1024 dims, excellent quality but large

## Trade-offs

- **Local inference** means no PHI leaves the infrastructure (critical for healthcare)
- **Trade-off**: Slightly lower quality than GPT-4/Claude for complex reasoning
- **Trade-off**: Higher latency than API calls for first request (model warmup)

## Future Considerations

- Can upgrade to `llama3.1:8b` for better quality if hardware allows
- Can swap embedding model to `mxbai-embed-large` (1024 dims) — would require schema migration
- For multilingual support, consider `bge-m3` which handles 100+ languages

## Related
- ADR-002: AI Sidecar Pattern vs. Embedded AI
- ADR-003: In-Memory vs. pgvector
