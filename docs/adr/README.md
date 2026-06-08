# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records (ADRs) for the FHIR Document Platform. ADRs document the *why* behind key technical decisions, not just the *what*.

## What is an ADR?

An ADR is a short, structured document that captures:
- The **context** (problem statement)
- The **decision** made
- The **consequences** (positive and negative)
- The **alternatives** considered

ADRs are immutable once accepted. Changes require a new ADR that supersedes the old one.

## Index

| # | Title | Status | Date |
|---|-------|--------|------|
| [ADR-001](ADR-001-fhir-facade-vs-full-server.md) | FHIR Facade vs. Full FHIR Server | Accepted | 2026-06-09 |
| [ADR-002](ADR-002-ai-sidecar-pattern.md) | AI Sidecar Pattern vs. Embedded AI | Accepted | 2026-06-09 |
| [ADR-003](ADR-003-in-memory-vs-pgvector.md) | In-Memory Store vs. PostgreSQL with pgvector | Accepted | 2026-06-09 |
| [ADR-004](ADR-004-model-selection.md) | Model Selection for LLM and Embeddings | Accepted | 2026-06-09 |
| [ADR-005](ADR-005-loinc-mapping.md) | LOINC Code Mapping Strategy | Accepted | 2026-06-09 |

## ADR Format

Each ADR follows this template:

```markdown
# ADR-NNN: <Title>

**Status**: Accepted | Superseded | Deprecated
**Date**: YYYY-MM-DD
**Deciders**: <Roles/Names>

## Context
What is the problem? What forces are at play?

## Decision
What did we choose? Include enough detail to be unambiguous.

## Consequences
### Positive
What becomes easier or better?

### Negative
What becomes harder or worse?

## Alternatives Considered
What other options were evaluated? Why were they rejected?

## Related
Links to related ADRs.
```

## How to Write a New ADR

1. Copy the template above into a new file: `ADR-NNN-short-kebab-case-title.md`
2. Replace NNN with the next sequential number (e.g., 006)
3. Fill in all sections
4. Add the entry to the index above
5. Link related ADRs at the bottom

## Why ADRs Matter

For a senior developer audience, ADRs demonstrate:
- **Architectural thinking** — considering trade-offs before writing code
- **Communication** — making decisions discoverable for future maintainers
- **Maturity** — recognizing that "we chose X" needs a *why*
- **Team alignment** — preventing repeated discussions of the same decisions
