from http.server import BaseHTTPRequestHandler, HTTPServer
from json import dumps, loads
from typing import Optional
from re import findall
from urllib.parse import urlparse


CLINICAL_TERMS = {
    "diabetes",
    "hypertension",
    "asthma",
    "cardiology",
    "radiology",
    "pathology",
    "discharge",
    "medication",
    "allergy",
    "lab",
    "imaging",
    "follow-up",
    "creatinine",
    "hemoglobin",
    "fracture",
    "infection",
}

STOP_WORDS = {
    "patient",
    "clinical",
    "document",
    "report",
    "normal",
    "history",
    "note",
    "summary",
}


def normalize_text(value: Optional[str]) -> str:
    return value.strip() if value else ""


def infer_title(content: str) -> str:
    compact = " ".join(content.split()).strip()
    if not compact:
        return "Untitled clinical document"
    return compact[:72]


def summarize(content: str) -> str:
    compact = " ".join(content.split()).strip()
    if not compact:
        return "AI sidecar has no document body yet; metadata is indexed from the submitted title and fields."
    if len(compact) <= 180:
        return compact
    return compact[:177] + "..."


def extract_keywords(text: str) -> list[str]:
    normalized = text.lower()
    keywords: list[str] = []

    for term in CLINICAL_TERMS:
        if term in normalized and term not in keywords:
            keywords.append(term)

    tokens = findall(r"[a-z0-9]+", normalized)
    candidates = [
        token
        for token in tokens
        if len(token) > 5 and token not in STOP_WORDS and token not in keywords
    ]

    candidates = sorted(set(candidates), key=len, reverse=True)[:4]
    for candidate in candidates:
        if candidate not in keywords:
            keywords.append(candidate)

    return keywords[:8]


def semantic_score(document: dict, query: Optional[str]) -> int:
    if not query or not query.strip():
        return 0

    normalized = query.lower()
    score = 0
    score += 8 if normalized in normalize_text(document.get("title")).lower() else 0
    score += 5 if normalized in normalize_text(document.get("documentType")).lower() else 0
    score += 4 if normalized in normalize_text(document.get("category")).lower() else 0
    score += 4 if normalized in normalize_text(document.get("patientName")).lower() else 0
    score += 3 if normalized in normalize_text(document.get("aiSummary")).lower() else 0

    for keyword in document.get("aiKeywords", []):
        if normalized in keyword.lower():
            score += 6

    return score


def enrich_document(payload: dict) -> dict:
    content = normalize_text(payload.get("content"))
    title = normalize_text(payload.get("title")) or infer_title(content)
    summary = summarize(content)
    keywords = extract_keywords(f"{title} {content}")

    return {
        "title": title,
        "summary": summary,
        "keywords": keywords,
    }


class SidecarHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send_json(self, status: int, payload: dict) -> None:
        body = dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/health":
            self._send_json(200, {"status": "ok", "service": "ai-sidecar"})
            return
        self._send_json(404, {"error": "not_found"})

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        print(f"{path} {raw}", flush=True)
        payload = loads(raw or "{}")

        if path == "/enrich":
            self._send_json(200, enrich_document(payload))
            return

        if path == "/score":
            self._send_json(
                200,
                {
                    "score": semantic_score(payload.get("document", {}), payload.get("query")),
                },
            )
            return

        self._send_json(404, {"error": "not_found"})

    def log_message(self, format: str, *args) -> None:
        return


def main() -> None:
    host = "0.0.0.0"
    port = 8090
    server = HTTPServer((host, port), SidecarHandler)
    print(f"AI sidecar listening on http://{host}:{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
