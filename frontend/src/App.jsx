import { useEffect, useMemo, useState } from "react";
import { FileSearch, RefreshCw, Search, Upload } from "lucide-react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

function App() {
  const [documents, setDocuments] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [fhirReference, setFhirReference] = useState(null);
  const [query, setQuery] = useState("");
  const [patient, setPatient] = useState("");
  const [type, setType] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    patientId: "PAT-1003",
    patientName: "Nisha Varma",
    title: "Hypertension follow-up note",
    documentType: "Progress note",
    category: "clinical-note",
    content:
      "Follow-up clinical note for hypertension medication review and care plan. Patient reports improved home blood pressure readings.",
  });

  const selectedDocument = useMemo(
    () => documents.find((document) => document.id === selectedId) ?? documents[0],
    [documents, selectedId],
  );

  useEffect(() => {
    loadDocuments();
  }, []);

  useEffect(() => {
    if (selectedDocument) {
      loadFhirReference(selectedDocument.id);
    } else {
      setFhirReference(null);
    }
  }, [selectedDocument?.id]);

  async function loadDocuments() {
    setLoading(true);
    setError("");
    const params = new URLSearchParams();
    if (query) params.set("q", query);
    if (patient) params.set("patient", patient);
    if (type) params.set("type", type);

    try {
      const response = await fetch(`${API_BASE_URL}/api/document?${params}`);
      if (!response.ok) throw new Error(`API returned ${response.status}`);
      const data = await response.json();
      setDocuments(data);
      setSelectedId((current) => current ?? data[0]?.id ?? null);
    } catch (apiError) {
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadFhirReference(id) {
    try {
      const response = await fetch(`${API_BASE_URL}/fhir/DocumentReference/${id}`);
      if (!response.ok) throw new Error(`FHIR API returned ${response.status}`);
      setFhirReference(await response.json());
    } catch (apiError) {
      setFhirReference({ error: apiError.message });
    }
  }

  async function ingestDocument(event) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      const response = await fetch(`${API_BASE_URL}/api/document`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      if (!response.ok) throw new Error(`Ingest returned ${response.status}`);
      const created = await response.json();
      setSelectedId(created.id);
      await loadDocuments();
    } catch (apiError) {
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="topbar">
        <div>
          <p className="eyebrow">FHIR-Aware Clinical Document Service</p>
          <h1>DocumentReference Explorer</h1>
        </div>
        <button className="icon-button" onClick={loadDocuments} title="Refresh documents">
          <RefreshCw size={18} />
        </button>
      </section>

      <section className="toolbar">
        <label>
          <span>Search</span>
          <div className="input-with-icon">
            <Search size={16} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="semantic text" />
          </div>
        </label>
        <label>
          <span>Patient</span>
          <input value={patient} onChange={(event) => setPatient(event.target.value)} placeholder="PAT-1001" />
        </label>
        <label>
          <span>Type</span>
          <input value={type} onChange={(event) => setType(event.target.value)} placeholder="lab, imaging" />
        </label>
        <button className="primary-action" onClick={loadDocuments}>
          <FileSearch size={18} />
          Find
        </button>
      </section>

      {error && <div className="status-message">Backend unavailable: {error}</div>}

      <section className="workspace">
        <div className="results-panel">
          <div className="panel-heading">
            <h2>Documents</h2>
            <span>{loading ? "Loading" : `${documents.length} found`}</span>
          </div>
          <div className="document-list">
            {documents.map((document) => (
              <button
                className={`document-row ${document.id === selectedDocument?.id ? "active" : ""}`}
                key={document.id}
                onClick={() => setSelectedId(document.id)}
              >
                <strong>{document.title}</strong>
                <span>
                  {document.patientName} - {document.documentType}
                </span>
                <small>{document.aiKeywords.join(", ")}</small>
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
              </dl>
              <p className="summary">{selectedDocument.aiSummary}</p>
              <pre>{JSON.stringify(fhirReference, null, 2)}</pre>
            </>
          ) : (
            <div className="empty-state">No documents match the current filters.</div>
          )}
        </div>

        <form className="ingest-panel" onSubmit={ingestDocument}>
          <div className="panel-heading">
            <h2>Ingest</h2>
            <Upload size={18} />
          </div>
          {Object.entries(form).map(([key, value]) => (
            <label key={key}>
              <span>{labelFor(key)}</span>
              {key === "content" ? (
                <textarea value={value} onChange={(event) => setForm({ ...form, [key]: event.target.value })} />
              ) : (
                <input value={value} onChange={(event) => setForm({ ...form, [key]: event.target.value })} />
              )}
            </label>
          ))}
          <button className="primary-action" type="submit">
            <Upload size={18} />
            Ingest
          </button>
        </form>
      </section>
    </main>
  );
}

function labelFor(key) {
  return key.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase());
}

export default App;
