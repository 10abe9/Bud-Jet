"""Cleaning what the user sends and what the model returns."""

import json
import re
from typing import Any

MAX_REPLY_CHARS = 1500
MAX_INSIGHTS = 4
MAX_TITLE_CHARS = 60
MAX_TIP_CHARS = 300

_WHITESPACE = re.compile(r"\s+")
_LIST_MARKER = re.compile(r"^\s*(?:[-*+]|\d+[.)])\s+")
_HEADING = re.compile(r"^\s*#{1,6}\s*")


def normalize_message(message: str) -> str:
    """Same rule as the app (AiChatPolicy): trim and collapse whitespace."""
    return _WHITESPACE.sub(" ", message).strip()


def _cut(text: str, limit: int) -> str:
    text = text.strip()
    if len(text) <= limit:
        return text
    cut = text[: limit - 1]
    space = cut.rfind(" ")
    if space > limit * 0.6:
        cut = cut[:space]
    return cut.rstrip(" ,.;:") + "…"


def clean_reply(text: str) -> str:
    """Plain text for the app: no markdown, lists as "• ", bounded length."""
    text = text.replace("```", "").replace("**", "").replace("__", "").replace("`", "")
    lines = []
    for line in text.splitlines():
        line = _HEADING.sub("", line)
        if _LIST_MARKER.match(line):
            line = "• " + _LIST_MARKER.sub("", line, count=1)
        lines.append(line.rstrip())
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()
    return _cut(text, MAX_REPLY_CHARS)


def _extract_json(raw: str) -> Any:
    try:
        return json.loads(raw)
    except ValueError:
        pass
    start, end = raw.find("{"), raw.rfind("}")
    if start == -1 or end <= start:
        raise ValueError("no JSON object")
    return json.loads(raw[start : end + 1])


def parse_insights(raw: str) -> list[dict[str, str]] | None:
    """Tips from the model's JSON, cleaned; None when the output is not usable JSON."""
    try:
        data = _extract_json(raw)
    except ValueError:
        return None
    items = data.get("insights") if isinstance(data, dict) else None
    if not isinstance(items, list):
        return None
    tips = []
    for item in items:
        if not isinstance(item, dict):
            continue
        title = clean_reply(str(item.get("title") or "")).replace("\n", " ")
        text = clean_reply(str(item.get("text") or "")).replace("\n", " ")
        if not text:
            continue
        tips.append({"title": _cut(title, MAX_TITLE_CHARS), "text": _cut(text, MAX_TIP_CHARS)})
        if len(tips) == MAX_INSIGHTS:
            break
    return tips


INSIGHTS_JSON_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "insights": {
            "type": "array",
            "maxItems": MAX_INSIGHTS,
            "items": {
                "type": "object",
                "properties": {"title": {"type": "string"}, "text": {"type": "string"}},
                "required": ["title", "text"],
            },
        }
    },
    "required": ["insights"],
}
