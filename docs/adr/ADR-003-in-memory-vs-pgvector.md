# ADR-003: In-Memory Store vs. PostgreSQL with pgvector

**Status**: Accepted
**Date**: 2026-06-09
**Deciders**: Project Lead

---

## Context

The platform needs to store clinical documents and support semantic search via embedding similarity. Two storage strategies were considered:

1. **In-memory ConcurrentHashMap** — Documents stored in a Java `ConcurrentHashMap` with local keyword-based semantic scoring
2. **PostgreSQL with pgvector** — Documents persisted in PostgreSQL with the pgvector extension for native cosine similarity search

The current implementation supports **both** modes, selected via the `pgvector` Spring profile.

## Decision

We chose a **dual-mode architecture**:
- **Default (dev)**: In-memory store with `ConcurrentHashMap` + keyword scoring
- **pgvector profile**: PostgreSQL + pgvector for persistent storage and native vector search

The mode is selected at startup via `useJpa = vectorSearchService.isPresent()` in `DocumentService`.

## Consequences

**Positive**:
- Zero infrastructure for development — no database setup required
- Fast iteration — restart clears state, no migration needed
- pgvector uses native PostgreSQL `<=>` operator for true cosine similarity, not keyword heuristics
- JPA mode enables standard Spring Data patterns (repositories, transactions, lazy loading)
- Custom `VectorConverter` handles `float[]` ↔ TEXT ↔ pgvector `vector` type transparently
- Graceful fallback: if pgvector index creation fails, the service logs a warning and continues

**Negative**:
- Dual code paths (`useJpa` branching in `DocumentService`, `search()`, `ingest()`) increase maintenance
- In-memory mode loses data on restart (acceptable for dev/demo)
- pgvector requires PostgreSQL 13+ with the extension installed
- The custom `VectorConverter` serializes vectors as comma-separated strings — not optimal for performance
- H2 (default dev) doesn't support pgvector, so JPA tests use a different storage path

## Alternatives Considered

### H2 with pgvector mock
- Use H2's array type and implement in-JVM cosine similarity
- **Rejected**: H2 arrays don't behave like pgvector; would need to maintain two native query implementations

### MongoDB with Atlas Vector Search
- Native vector index support
- **Rejected**: Would add a third infrastructure dependency; team prefers relational model for document metadata

### Elasticsearch with dense_vector
- Production-grade vector search with hybrid (BM25 + vector) scoring
- **Rejected**: Overkill for this project size; would require a separate ES cluster

## Performance Considerations

The in-memory semantic scoring uses weighted keyword matching:
- Title match: 8× weight
- Keyword match: 6× weight
- Type/category: 4-5× weight
- Summary: 3× weight

This is a heuristic — not true semantic similarity. For production, the pgvector path with actual embedding vectors is preferred.

## Related
- ADR-002: AI Sidecar Pattern vs. Embedded AI
- ADR-004: Model Selection for LLM and Embeddings