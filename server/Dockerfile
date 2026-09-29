FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Unprivileged user; /data holds the SQLite file (named volume, keeps this ownership).
RUN useradd --create-home --uid 1000 budjet && mkdir -p /data && chown budjet /data
USER budjet

EXPOSE 8000
# One worker: rate-limit counters in SQLite are exact within one process, and the work is
# waiting on Google and Ollama, which asyncio handles concurrently. Only Caddy can reach this
# port, so forwarded headers are trusted. Our own middleware logs requests (no bodies).
CMD ["uvicorn", "app.asgi:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", \
     "--proxy-headers", "--forwarded-allow-ips", "*", "--no-access-log"]
