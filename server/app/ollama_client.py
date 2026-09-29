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
            # The body may echo the prompt, so only the status is logged.
            raise ModelError(f"http {response.status_code}")
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
