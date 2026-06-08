from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Server
    host: str = "0.0.0.0"
    port: int = 8090

    # Ollama
    ollama_base_url: str = "http://localhost:11434"
    llm_model: str = "phi3.5:latest"
    embed_model: str = "nomic-embed-text:latest"

    # Timeouts (seconds)
    llm_timeout: int = 30
    embed_timeout: int = 10

    # Limits
    max_content_length: int = 50_000  # characters

    model_config = {"env_prefix": "AI_", "env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()