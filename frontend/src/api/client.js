/**
 * API client for the backend document service.
 * Uses relative URLs which are proxied by Vite during development.
 */

const BASE = "";

/**
 * Search clinical documents with optional filters.
 * @param {{ query?: string, patient?: string, type?: string }} params
 * @returns {Promise<Array>}
 */
export async function searchDocuments({ query, patient, type } = {}) {
  const params = new URLSearchParams();
  if (query) params.set("q", query);
  if (patient) params.set("patient", patient);
  if (type) params.set("type", type);

  const response = await fetch(`${BASE}/api/document?${params}`);
  if (!response.ok) throw new Error(`API returned ${response.status}`);
  return response.json();
}

/**
 * Get a single document by ID.
 * @param {string} id
 * @returns {Promise<Object>}
 */
export async function getDocument(id) {
  const response = await fetch(`${BASE}/api/document/${encodeURIComponent(id)}`);
  if (!response.ok) throw new Error(`API returned ${response.status}`);
  return response.json();
}

/**
 * Get the FHIR DocumentReference representation for a document.
 * @param {string} id
 * @returns {Promise<Object>}
 */
export async function getFhirReference(id) {
  const response = await fetch(`${BASE}/fhir/DocumentReference/${encodeURIComponent(id)}`);
  if (!response.ok) throw new Error(`FHIR API returned ${response.status}`);
  return response.json();
}

/**
 * Ingest a new document (will be enriched by the AI sidecar).
 * @param {Object} data
 * @returns {Promise<Object>}
 */
export async function ingestDocument(data) {
  const response = await fetch(`${BASE}/api/document`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  if (!response.ok) throw new Error(`Ingest returned ${response.status}`);
  return response.json();
}