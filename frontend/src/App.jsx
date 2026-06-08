import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FileSearch, RefreshCw, Search, Upload } from "lucide-react";
import {
  searchDocuments,
  getFhirReference,
  ingestDocument,
} from "./api/client.js";

const FORM_FIELDS = [
  { key: "patientId", label: "Patient ID", defaultValue: "PAT-1003" },
  { key: "patientName", label: "Patient Name", defaultValue: "Nisha Varma" },
  { key: "title", label: "Title", defaultValue: "Hypertension follow-up note" },
  { key: "documentType", label: "Document Type", defaultValue: "Progress note" },
  { key: "category", label: "Category", defaultValue: "clinical-note" },
  {
    key: "content",
    label: "Content",
    defaultValue:
      "Follow-up clinical note for hypertension medication review and care plan. Patient reports improved home blood pressure readings.",
    multiline: true,
  },
];

function initFormDefaults() {
  const defaults = {};
  for (const field of FORM_FIELDS) {
    defaults[field.key] = field.defaultValue;
  }
  return defaults;
}

// ---------------------------------------------------------------------------
// useDebounce hook
// ---------------------------------------------------------------------------

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}

// ---------------------------------------------------------------------------
// App component
// ---------------------------------------------------------------------------

function App() {
  // Document list state
  const [documents, setDocuments] = useState([]);
  const [selectedId, setSelectedId] = useState(null);

  // FHIR state
  const [fhirReference, setFhirReference] = useState(null);

  // Search state
  const [query, setQuery] = useState("");
  const [patient, setPatient] = useState("");
  const [type, setType] = useState("");
  const debouncedQuery = useDebounce(query, 300);
  const debouncedPatient = useDebounce(patient, 300);
  const debouncedType = useDebounce(type, 300);

  // Loading states (separate per action)
  const [searchLoading, setSearchLoading] = useState(false);
  const [fhirLoading, setFhirLoading] = useState(false);
  const [ingestLoading, setIngestLoading] = useState(false);

  // Error state
  const [error, setError] = useState("");

  // Ingest form state
  const [form, setForm] = useState(initFormDefaults);
  const [formErrors, setFormErrors] = useState({});

  // Track initial mount for debounce skip
  const isFirstRender = useRef(true);

  const selectedDocument = useMemo(
    () => documents.find((document) => document.id === selectedId) ?? documents[0],
    [documents, selectedId],
  );

  // -----------------------------------------------------------------------
  // Load documents
  // -----------------------------------------------------------------------

  const loadDocuments = useCallback(async function loadDocuments(overrides = {}) {
    setSearchLoading(true);
    setError("");

    try {
      const params = {
        query: overrides.query ?? debouncedQuery,
        patient: overrides.patient ?? debouncedPatient,
        type: overrides.type ?? debouncedType,
      };
      const data = await searchDocuments(params);
      setDocuments(data);
      setSelectedId((current) => current ?? data[0]?.id ?? null);
    } catch (apiError) {
      setError(apiError.message);
    } finally {
      setSearchLoading(false);
    }
  }, [debouncedQuery, debouncedPatient, debouncedType]);

  // -----------------------------------------------------------------------
  // Load FHIR reference for selected document
  // -----------------------------------------------------------------------

  const loadFhirReference = useCallback(async function loadFhirReference(id) {
    if (!id) {
      setFhirReference(null);
      return;
    }

    setFhirLoading(true);
    try {
      setFhirReference(await getFhirReference(id));
    } catch (apiError) {
      setFhirReference({ error: apiError.message });
    } finally {
      setFhirLoading(false);
    }
  }, []);

  // -----------------------------------------------------------------------
  // Effects
  // -----------------------------------------------------------------------

  // Initial load
  useEffect(() => {
    loadDocuments();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Debounced auto-search: skip the first render (already loaded above)
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    loadDocuments();
  }, [debouncedQuery, debouncedPatient, debouncedType]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load FHIR when selected document changes
  useEffect(() => {
    loadFhirReference(selectedDocument?.id);
  }, [selectedDocument?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // -----------------------------------------------------------------------
  // Ingest document
  // -----------------------------------------------------------------------

  async function handleIngest(event) {
    event.preventDefault();

    // Validate form
    const errors = {};
    if (!form.patientId.trim()) errors.patientId = "Required";
    if (!form.title.trim()) errors.title = "Required";
    if (!form.content.trim()) errors.content = "Required";
    setFormErrors(errors);

    if (Object.keys(errors).length > 0) return;

    setIngestLoading(true);
    setError("");

    try {
      const created = await ingestDocument(form);
      setSelectedId(created.id);
      // Reload documents list in the background
      loadDocuments().catch(() => {});
    } catch (apiError) {
      setError(apiError.message);
    } finally {
      setIngestLoading(false);
    }
  }

  function handleFormChange(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
    // Clear field error on change
    if (formErrors[key]) {
      setFormErrors((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  }

  // -----------------------------------------------------------------------
  // Render
  // -----------------------------------------------------------------------

  return (
    <main className="app-shell">
      <section className="topbar">
        <div>
          <p className="eyebrow">FHIR-Aware Clinical Document Service</p>
          <h1>DocumentReference Explorer</h1>
        </div>
        <button
          className="icon-button"
          onClick={loadDocuments}
          disabled={searchLoading}
          title="Refresh documents"
        >
          <RefreshCw size={18} className={searchLoading ? "spinning" : ""} />
        </button>
      </section>

      <section className="toolbar">
        <label>
          <span>Search</span>
          <div className="input-with-icon">
            <Search size={16} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="semantic text (auto-search)"
            />
          </div>
        </label>
        <label>
          <span>Patient</span>
          <input
            value={patient}
            onChange={(event) => setPatient(event.target.value)}
            placeholder="PAT-1001"
          />
        </label>
        <label>
          <span>Type</span>
          <input
            value={type}
            onChange={(event) => setType(event.target.value)}
            placeholder="lab, imaging"
          />
        </label>
        <button className="primary-action" onClick={loadDocuments} disabled={searchLoading}>
          <FileSearch size={18} />
          Find
        </button>
      </section>

      {error && <div className="status-message">Backend unavailable: {error}</div>}

      <section className="workspace">
        <div className="results-panel">
          <div className="panel-heading">
            <h2>Documents</h2>
            <span>
              {searchLoading
                ? "Searching..."
                : `${documents.length} found`}
            </span>
          </div>
          <div className="document-list">
            {documents.length === 0 && !searchLoading && (
              <div className="empty-state-small">No documents match the current filters.</div>
            )}
            {documents.map((document) => (
              <button
                className={`document-row ${document.id === selectedDocument?.id ? "active" : ""}`}
                key={document.id}
                onClick={() => setSelectedId(document.id)}
              >
                <strong>{document.title}</strong>
                <span>
                  {document.patientName} &ndash; {document.documentType}
                </span>
                <small>{document.aiKeywords?.join(", ") ?? ""}</small>
              </button>
            ))}
          </div>
        </div>

        <div className="details-panel">
          {selectedDocument ? (
            <>
              <div className="panel-heading">
                <h2>{selectedDocument.title}</h2>
                <span>{selectedDocument.id}</span>
              </div>
              {selectedDocument.aiSummary && (
                <p className="summary">
                  <span className="ai-badge">AI summary</span>
                  {selectedDocument.aiSummary}
                </p>
              )}
              <dl className="metadata-grid">
                <div>
                  <dt>Patient</dt>
                  <dd>
                    {selectedDocument.patientName} ({selectedDocument.patientId})
                  </dd>
                </div>
                <div>
                  <dt>Source</dt>
                  <dd>{selectedDocument.sourceSystem}</dd>
                </div>
                <div>
                  <dt>Author</dt>
                  <dd>{selectedDocument.author}</dd>
                </div>
                <div>
                  <dt>Created</dt>
                  <dd>{new Date(selectedDocument.createdAt).toLocaleString()}</dd>
                </div>
                {selectedDocument.aiKeywords?.length > 0 && (
                  <div>
                    <dt>AI Keywords</dt>
                    <dd>{selectedDocument.aiKeywords.join(", ")}</dd>
                  </div>
                )}
              </dl>
              <div className="panel-heading">
                <h3>FHIR DocumentReference</h3>
                {fhirLoading && <span className="loading-tag">Loading...</span>}
              </div>
              <pre>{JSON.stringify(fhirReference, null, 2)}</pre>
            </>
          ) : (
            <div className="empty-state">
              {searchLoading ? "Searching..." : "No documents match the current filters."}
            </div>
          )}
        </div>

        <form className="ingest-panel" onSubmit={handleIngest}>
          <div className="panel-heading">
            <h2>Ingest</h2>
            <Upload size={18} />
          </div>
          {FORM_FIELDS.map(({ key, label, multiline }) => (
            <label key={key}>
              <span>
                {label}
                {formErrors[key] && <span className="field-error"> &mdash; {formErrors[key]}</span>}
              </span>
              {multiline ? (
                <textarea
                  value={form[key]}
                  onChange={(event) => handleFormChange(key, event.target.value)}
                  className={formErrors[key] ? "input-error" : ""}
                />
              ) : (
                <input
                  value={form[key]}
                  onChange={(event) => handleFormChange(key, event.target.value)}
                  className={formErrors[key] ? "input-error" : ""}
                />
              )}
            </label>
          ))}
          <button className="primary-action" type="submit" disabled={ingestLoading}>
            <Upload size={18} />
            {ingestLoading ? "Ingesting..." : "Ingest"}
          </button>
        </form>
      </section>
    </main>
  );
}

export default App;