# ADR-005: LOINC Code Mapping Strategy

**Status**: Accepted
**Date**: 2026-06-09
**Deciders**: Project Lead

---

## Context

FHIR `DocumentReference.type` and `category` require coded values from standard medical terminologies. LOINC (Logical Observation Identifiers Names and Codes) is the de facto standard for clinical document types.

The platform needs to map free-text document types (e.g., "Discharge summary") to LOINC codes (e.g., `18842-5`).

## Decision

We chose a **hardcoded mapping table** in `DocumentService` with two `Map<String, String>` lookups:
- `LOINC_DOC_MAP` — maps document type strings to LOINC document codes
- `LOINC_CATEGORY_MAP` — maps category strings to LOINC list codes

The lookup is done via `lookupLoincCode()` which uses case-insensitive substring matching.

## Consequences

**Positive**:
- Zero external dependencies — no terminology service to deploy
- Fast lookup — `O(n)` scan of a small map (~10 entries)
- Easy to extend — just add a new entry to the map
- Testable — pure function, no I/O

**Negative**:
- Only covers the most common document types (~10)
- Unknown types fall through to a default code (`34108-1` Clinical note) which may be incorrect
- The substring matching can produce false positives ("radiology lab" would match "lab")
- No version tracking — if LOINC updates a code, the map is stale until updated
- Doesn't validate against the official LOINC distribution

## Mapping Reference

### Document Types
| Input Pattern | LOINC Code | Display |
|---------------|-----------|---------|
| `discharge summary` | `18842-5` | Discharge summary |
| `radiology report`, `imaging` | `18748-4` | Diagnostic imaging report |
| `laboratory report`, `lab` | `26436-6` | Laboratory report |
| `clinical note` | `34108-1` | Clinical note |
| `progress note` | `11506-3` | Progress note |
| `consultation` | `11488-3` | Consultation note |
| `procedure note` | `28570-0` | Procedure note |
| `pathology report` | `11526-1` | Pathology report |

### Categories
| Input | LOINC List Code | Display |
|-------|----------------|---------|
| `clinical-note` | `LP173421-1` | Clinical note |
| `imaging` | `LP29693-6` | Imaging |
| `lab`, `pathology` | `LP7839-6` | Laboratory |
| `cardiology` | `LP104197-4` | Cardiology |

## Alternatives Considered

### External Terminology Service (Snowstorm, Ontoserver)
- Industry-standard FHIR terminology server
- Supports full LOINC, SNOMED CT, ICD-10, RxNorm
- **Rejected**: Heavy infrastructure dependency, requires a separate service

### LOINC Distribution File Import
- Download official LOINC CSV/Access file
- Parse and load into PostgreSQL
- **Rejected**: LOINC distribution requires license agreement; complexity not justified for demo project

### Free-text search (`_text` parameter)
- Skip coding entirely, use full-text search
- **Rejected**: Loses semantic interoperability, breaks FHIR conformance

## Trade-offs

- **Trade-off**: We accept partial LOINC coverage for a simpler implementation
- **Trade-off**: We accept potential false-positive matches in the substring lookup
- **Trade-off**: We accept the need to manually update the map as new document types are added

## Future Considerations

- Replace with Snowstorm/Ontoserver integration for production
- Move to database-stored mappings with admin UI
- Add validation against the LOINC subset relevant to clinical documents

## Related
- ADR-001: FHIR Facade vs. Full FHIR Server
