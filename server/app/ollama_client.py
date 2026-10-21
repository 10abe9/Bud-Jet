"""Calls to Ollama Cloud (POST {base}/api/chat)."""

import logging
import re
from typing import Any

import httpx

log = logging.getLogger("budjet.ollama")

THINK_TAGS = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)


class ModelTimeout(Exception):
    pass


class ModelError(Exception):
    pass


def _error_detail(response: httpx.Response) -> str:
    """Ollama's short error text (e.g. "model 'x' not found"); never the request or reply."""
    try:
        error = response.json().get("error")
    except (ValueError, AttributeError):
        return ""
    if not isinstance(error, str) or not error:
        return ""
    return ": " + error.replace("\n", " ")[:200]


class OllamaClient:
    def __init__(
        self,
        http: httpx.AsyncClient,
        base_url: str,
        api_key: str,
        model: str,
        think: str = "",
        timeout_seconds: float = 45.0,
    ):
        self._http = http
        self._url = base_url.rstrip("/") + "/api/chat"
        self._api_key = api_key
        self._model = model
        self._think = think
        self._timeout = timeout_seconds

    async def list_models(self) -> list[str]:
        """Model names this API key can use (GET {base}/api/tags)."""
        url = self._url.removesuffix("/api/chat") + "/api/tags"
        response = await self._http.get(url, headers={"Authorization": f"Bearer {self._api_key}"}, timeout=20.0)
        if response.status_code != 200:
            raise ModelError(f"http {response.status_code}{_error_detail(response)}")
        return sorted(m.get("name") or m.get("model", "") for m in response.json().get("models", []))

    async def chat(
        self,
        messages: list[dict[str, str]],
        *,
        temperature: float,
        num_predict: int,
        json_schema: dict[str, Any] | None = None,
    ) -> str:
        body: dict[str, Any] = {
            "model": self._model,
            "messages": messages,
            "stream": False,
            "options": {"temperature": temperature, "num_predict": num_predict},
        }
        if self._think:
            body["think"] = self._think
        if json_schema is not None:
            body["format"] = json_schema

        try:
            response = await self._http.post(
                self._url,
                json=body,
                headers={"Authorization": f"Bearer {self._api_key}"},
                timeout=self._timeout,
            )
        except httpx.TimeoutException as error:
            raise ModelTimeout() from error
        except httpx.HTTPError as error:
            raise ModelError(type(error).__name__) from error

        if response.status_code != 200:
            raise ModelError(f"http {response.status_code}{_error_detail(response)}")
        try:
            data = response.json()
            content = data["message"]["content"]
        except (ValueError, KeyError, TypeError) as error:
            raise ModelError("unexpected response") from error

        log.info(
            "model=%s prompt_tokens=%s output_tokens=%s",
            self._model,
            data.get("prompt_eval_count"),
            data.get("eval_count"),
        )
        return THINK_TAGS.sub("", content or "").strip()
