"""A small HTTP helper with a sane User-Agent and bounded retries."""

from __future__ import annotations

import time

import requests


class HttpClient:
    """Thin wrapper around :class:`requests.Session`.

    Centralises the User-Agent (Google News and many sites reject the default
    urllib/requests agents with a 403) and adds a simple retry-with-backoff.
    """

    def __init__(self, user_agent: str, timeout: int = 15, max_retries: int = 3):
        self.timeout = timeout
        self.max_retries = max_retries
        self.session = requests.Session()
        self.session.headers.update(
            {
                "User-Agent": user_agent,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "en-US,en;q=0.9",
            }
        )

    def get(self, url: str) -> requests.Response | None:
        """GET ``url``, returning the response or ``None`` on persistent failure."""
        backoff = 1.0
        last_error: Exception | None = None
        for attempt in range(self.max_retries):
            try:
                resp = self.session.get(url, timeout=self.timeout)
                resp.raise_for_status()
                return resp
            except requests.RequestException as exc:  # network or HTTP error
                last_error = exc
                if attempt < self.max_retries - 1:
                    time.sleep(backoff)
                    backoff *= 2
        # Surface the failure to the caller without crashing the whole run.
        print(f"  ! request failed for {url}: {last_error}")
        return None

    def close(self) -> None:
        self.session.close()
