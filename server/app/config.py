"""Settings from environment variables (or a .env file next to the app)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Ollama Cloud (https://ollama.com/api/chat). The key never leaves the server.
    ollama_api_key: str = ""
    ollama_base_url: str = "https://ollama.com"
    ollama_model: str = "gpt-oss:20b"
    # Empty, or low/medium/high for models that support thinking levels (gpt-oss).
    ollama_think: str = ""
    # The app waits 60 s for an AI answer; stay below that.
    ollama_timeout_seconds: float = 45.0

    # Google Play Developer API (subscription checks).
    google_application_credentials: str = "/secrets/service-account.json"
    package_name: str = "com.abe.bud_jet"
    pro_product_id: str = "bud_jet_premium"
    basic_product_id: str = "bud_jet_base"

    db_path: str = "/data/budjet.sqlite3"

    # Limits. Per subscription per UTC day unless noted.
    chat_daily_limit: int = 30
    insights_daily_limit: int = 10
    install_daily_ai_limit: int = 60
    ip_hourly_limit: int = 120
    verify_hourly_limit_per_install: int = 30

    max_body_bytes: int = 64 * 1024

    @property
    def product_ids(self) -> set[str]:
        return {self.pro_product_id, self.basic_product_id}


@lru_cache
def get_settings() -> Settings:
    return Settings()
