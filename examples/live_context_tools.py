"""
Live-context fetch tools — Wikipedia, Reddit, TSA checkpoint volumes.

Companion to grok_sql_agent.py (any OpenAI-compatible agent can use them).
Each fetcher returns a COMPACT JSON string (top-N, truncated) so tool results
don't flood the context window. See docs/database/live-sources.md for how the
DB pointer tables target these fetches.

Security: fetched content is untrusted. These tools only return text for the
model to summarize; the agent contract (AGENTS.md) forbids treating it as
instructions.

    pip install requests
"""
import json
import re

import requests

UA = {"User-Agent": "terminal05-data-agent/1.0 (read-only analytics)"}
TRUNC = 400  # max chars per text field returned to the model


def _clip(s, n=TRUNC):
    s = re.sub(r"\s+", " ", s or "").strip()
    return s[:n] + ("…" if len(s) > n else "")


def wikipedia_fetch(title: str) -> str:
    """Live summary of an English Wikipedia page (no key needed)."""
    try:
        r = requests.get(
            f"https://en.wikipedia.org/api/rest_v1/page/summary/{requests.utils.quote(title)}",
            headers=UA, timeout=10,
        )
        r.raise_for_status()
        d = r.json()
        return json.dumps({
            "title": d.get("title"),
            "description": d.get("description"),
            "summary": _clip(d.get("extract"), 900),
            "url": (d.get("content_urls") or {}).get("desktop", {}).get("page"),
        })
    except Exception as e:
        return json.dumps({"error": f"wikipedia_fetch failed: {e}"})


def reddit_fetch(subreddit: str, limit: int = 8) -> str:
    """Current hot threads in a subreddit (use performer_subreddits/general_subreddits
    to pick the subreddit). Low-volume JSON reads; for sustained use register an OAuth app."""
    try:
        r = requests.get(
            f"https://www.reddit.com/r/{subreddit}/hot.json",
            params={"limit": min(int(limit), 15)}, headers=UA, timeout=10,
        )
        r.raise_for_status()
        posts = [{
            "title": _clip(c["data"].get("title"), 200),
            "score": c["data"].get("score"),
            "comments": c["data"].get("num_comments"),
            "flair": c["data"].get("link_flair_text"),
        } for c in r.json().get("data", {}).get("children", [])
            if not c["data"].get("stickied")]
        return json.dumps({"subreddit": subreddit, "hot": posts[:limit]})
    except Exception as e:
        return json.dumps({"error": f"reddit_fetch failed: {e}"})


def tsa_fetch(days: int = 14) -> str:
    """TSA daily checkpoint screening volumes (national air-travel demand trend).
    Returns the most recent N days with prior-year same-weekday comparison and a
    7-day YoY summary. Source: tsa.gov/travel/passenger-volumes (no key)."""
    try:
        r = requests.get("https://www.tsa.gov/travel/passenger-volumes",
                         headers=UA, timeout=15)
        r.raise_for_status()
        # Parse the HTML table: rows of <td>Date</td><td>2026 count</td><td>2025 count</td>...
        rows = re.findall(r"<tr[^>]*>(.*?)</tr>", r.text, re.S)
        out = []
        for row in rows:
            cells = [re.sub(r"<[^>]+>", "", c).strip()
                     for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
            if len(cells) >= 2 and re.match(r"\d{1,2}/\d{1,2}/\d{4}", cells[0]):
                out.append({
                    "date": cells[0],
                    "current": cells[1],
                    "prior_years": cells[2:5],
                })
        out = out[:max(int(days), 7)]
        if not out:
            return json.dumps({"error": "tsa_fetch: no rows parsed (page layout may have changed)"})

        def n(s):  # "2,406,790" -> int
            try:
                return int(s.replace(",", ""))
            except Exception:
                return None

        cur = [n(d["current"]) for d in out[:7] if n(d["current"])]
        pri = [n(d["prior_years"][0]) for d in out[:7]
               if d["prior_years"] and n(d["prior_years"][0])]
        yoy = (round((sum(cur) / len(cur)) / (sum(pri) / len(pri)) - 1, 4)
               if cur and pri else None)
        return json.dumps({
            "note": "national checkpoint volumes; macro travel-demand signal, not city-level",
            "yoy_7day_avg": yoy,
            "daily": out,
        })
    except Exception as e:
        return json.dumps({"error": f"tsa_fetch failed: {e}"})


# --- OpenAI-compatible tool specs + dispatch (import these from the agent) ---
LIVE_TOOLS = [
    {"type": "function", "function": {
        "name": "wikipedia_fetch",
        "description": "Fetch a live English Wikipedia summary for a page title. "
                       "Use performer_wikipedia in the DB to find the right title.",
        "parameters": {"type": "object", "properties": {
            "title": {"type": "string"}}, "required": ["title"]},
    }},
    {"type": "function", "function": {
        "name": "reddit_fetch",
        "description": "Fetch current hot threads from a subreddit for fan buzz/sentiment. "
                       "Pick the subreddit from performer_subreddits or general_subreddits.",
        "parameters": {"type": "object", "properties": {
            "subreddit": {"type": "string"},
            "limit": {"type": "integer"}}, "required": ["subreddit"]},
    }},
    {"type": "function", "function": {
        "name": "tsa_fetch",
        "description": "Fetch live TSA daily checkpoint screening volumes with 7-day YoY "
                       "trend — national air-travel demand signal for tourist-heavy events.",
        "parameters": {"type": "object", "properties": {
            "days": {"type": "integer"}}, "required": []},
    }},
]

DISPATCH = {
    "wikipedia_fetch": lambda a: wikipedia_fetch(a["title"]),
    "reddit_fetch": lambda a: reddit_fetch(a["subreddit"], a.get("limit", 8)),
    "tsa_fetch": lambda a: tsa_fetch(a.get("days", 14)),
}
