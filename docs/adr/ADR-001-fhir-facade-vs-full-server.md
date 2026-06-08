# ADR-001: FHIR Facade vs. Full FHIR Server

**Status**: Accepted
**Date**: 2026-06-09
**Deciders**: Project Lead

---

## Context

The project needs to expose healthcare documents through a FHIR-compatible API. Two approaches were considered:
1. Build a full FHIR server (using HAPI FHIR or similar)
2. Build a lightweight FHIR facade that manually constructs DocumentReference resources

The goal is to enable document discovery and metadata enrichment without the operational complexity of a full FHIR server.

## Decision

We chose a **FHIR facade** approach — the backend manually constructs JSON representations of `DocumentReference` resources in `DocumentService.toFhirDocumentReference()`. There is no FHIR validation engine, no FHIRPath evaluator, and no FHIR resource repository.

## Consequences

**Positive**:
- Minimal dependencies (no HAPI FHIR library, no FHIR registry)
- Full control over the JSON shape — easy to add AI metadata extensions
- Fast startup and low memory footprint
- Easy to understand and modify for a small team
- Clear separation: FHIR is an *output format*, not the storage model

**Negative**:
- No automatic FHIR validation — malformed resources won't be caught
- Must manually track spec changes (R4 → R4B → R5)
- No support for advanced FHIR operations like `$docref`, `$validate`, or history
- No FHIR search parameter framework — custom filtering logic only
- Not suitable for cross-system FHIR exchange without additional validation

## Alternatives Considered

### HAPI FHIR Server
- Full FHIR R4 server implementation
- Automatic validation, search, and resource management
- **Rejected**: Heavy dependency (~30MB+), complex configuration, overkill for a document explorer

### Dedicated FHIR Service (e.g., Azure FHIR, Google Healthcare API)
- Managed FHIR server with compliance guarantees
- **Rejected**: Cloud dependency, vendor lock-in, latency to sidecar

## Maturity Level

This places the project at **FHIR Maturity Level 0-1** (defined by HL7):
- Level 0: Resource is defined and published
- Level 1: No additional standardization (current state)
- The facade is *not* a production FHIR server — it's an educational/demo tool

## Related
- ADR-002: AI Sidecar Pattern vs. Embedded AI
- ADR-005: LOINC Code Mapping Strategy