FROM python:3.12-slim

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy service code
COPY config.py main.py llm_client.py ./

EXPOSE 8090

# Run with uvicorn (single worker since Ollama is a separate service)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]