"""Try the AI prompts against Ollama Cloud with a sample budget, without the app or Google.

    python -m scripts.try_prompt "How can I spend less?" --lang ru
    python -m scripts.try_prompt --insights --lang en
    python -m scripts.try_prompt "Write me a poem" --summary my_summary.json

Reads OLLAMA_API_KEY / OLLAMA_MODEL / OLLAMA_THINK from the environment or .env.
"""

import argparse
import asyncio
import json
from datetime import datetime, timezone

import httpx

from app import prompts
from app.config import Settings
from app.facts import build_facts
from app.ollama_client import OllamaClient
from app.schemas import BudgetSummary
from app.text import INSIGHTS_JSON_SCHEMA, clean_reply, normalize_message, parse_insights

SAMPLE = {
    "currency": "USD",
    "months": [
        {"month": "2026-07", "income": 3000.0, "expense": 1850.4,
         "expenseByCategory": {"Food": 720.5, "Transport": 310.0, "Entertainment": 240.0}},
        {"month": "2026-08", "income": 3000.0, "expense": 2100.0,
         "expenseByCategory": {"Food": 980.0, "Transport": 290.0, "Entertainment": 330.0}},
        {"month": "2026-09", "income": 3000.0, "expense": 1240.9,
         "expenseByCategory": {"Food": 610.0, "Transport": 150.0, "Entertainment": 210.0}},
    ],
    "limits": [{"category": "Food", "limit": 800.0, "spentThisMonth": 610.0}],
    "savingGoal": {"target": 5000.0, "saved": 1200.0, "deadline": "2026-12-31"},
}


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("question", nargs="?", default="How can I spend less?")
    parser.add_argument("--insights", action="store_true", help="ask for tips instead of chat")
    parser.add_argument("--lang", default="en", choices=sorted(prompts.LANGUAGE_NAMES))
    parser.add_argument("--summary", help="JSON file with a summary in the app's format")
    parser.add_argument("--show-prompt", action="store_true")
    args = parser.parse_args()

    settings = Settings()
    if not settings.ollama_api_key:
        raise SystemExit("Set OLLAMA_API_KEY (environment or .env)")
    data = json.load(open(args.summary, encoding="utf-8")) if args.summary else SAMPLE
    summary = BudgetSummary.model_validate(data)
    today = datetime.now(timezone.utc).date()
    template = prompts.INSIGHTS_SYSTEM_PROMPT if args.insights else prompts.CHAT_SYSTEM_PROMPT
    system = prompts.render(
        template, language=args.lang, currency=summary.currency, today=today,
        summary=summary.model_dump(), facts=build_facts(summary, today),
    )
    if args.show_prompt:
        print(system, "\n" + "-" * 60)

    async with httpx.AsyncClient() as http:
        ollama = OllamaClient(
            http, settings.ollama_base_url, settings.ollama_api_key, settings.ollama_model,
            settings.ollama_think, settings.ollama_timeout_seconds,
        )
        if args.insights:
            raw = await ollama.chat(
                [{"role": "system", "content": system}, {"role": "user", "content": prompts.INSIGHTS_USER_MESSAGE}],
                temperature=0.2, num_predict=700, json_schema=INSIGHTS_JSON_SCHEMA,
            )
            print(json.dumps({"insights": parse_insights(raw)}, ensure_ascii=False, indent=2))
        else:
            raw = await ollama.chat(
                [{"role": "system", "content": system},
                 {"role": "user", "content": normalize_message(args.question)}],
                temperature=0.3, num_predict=400,
            )
            print(clean_reply(raw))


if __name__ == "__main__":
    asyncio.run(main())
