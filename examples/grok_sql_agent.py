#!/usr/bin/env python3
"""
Terminal .5 database -> read-only SQL agent (provider-agnostic).

A minimal reference agent that answers questions about the data by writing and
running read-only SQL. It uses plain OpenAI-compatible function calling, so the
SAME script drives Claude (Sonnet), Grok, Gemini, GPT, or a local model — you
only change three env vars.

  Model decides what to query  ->  calls run_sql  ->  we execute it against the
  read-only Postgres role  ->  model interprets the rows and answers.

Setup (default: Claude Sonnet via Anthropic's OpenAI-compatible endpoint):
    pip install "openai>=1.0" "psycopg[binary]>=3.1"
    export AGENT_API_KEY="sk-ant-..."     # provider key (see table below)
    export DATABASE_URL="postgresql://data_reader:PW@db.hzrizjeaxlqcxfrtczpq.supabase.co:5432/postgres?sslmode=require"

Switch providers by setting AGENT_BASE_URL / AGENT_MODEL:
    Claude  (default) https://api.anthropic.com/v1/                claude-sonnet-4-6
    Grok              https://api.x.ai/v1                          grok-4
    Gemini            https://generativelanguage.googleapis.com/v1beta/openai/   gemini-2.5-flash
    Local (Ollama)    http://localhost:11434/v1                    qwen3:32b

Run:
    python examples/grok_sql_agent.py "What are the 5 priciest upcoming events by get-in price?"
"""
import os
import re
import sys
import json
import pathlib

from openai import OpenAI
import psycopg

# Provider config — defaults to Claude Sonnet. XAI_* names kept for back-compat.
BASE_URL = os.environ.get("AGENT_BASE_URL", "https://api.anthropic.com/v1/")
MODEL = os.environ.get("AGENT_MODEL", os.environ.get("XAI_MODEL", "claude-sonnet-4-6"))
API_KEY = (os.environ.get("AGENT_API_KEY") or os.environ.get("ANTHROPIC_API_KEY")
           or os.environ.get("XAI_API_KEY") or "")
DATABASE_URL = os.environ["DATABASE_URL"]
REPO = pathlib.Path(__file__).resolve().parents[1]

# --- Budget guard (client-side hard cap) --------------------------------------
# Tracks estimated spend in a local ledger and refuses to start/continue once the
# monthly cap is hit. This is the graceful in-month stop; ALSO set the hard spend
# limit in the provider console (workspace/org level) as the unbypassable backstop.
MONTHLY_BUDGET_USD = float(os.environ.get("MONTHLY_BUDGET_USD", "20"))
MAX_COST_PER_QUESTION_USD = float(os.environ.get("MAX_COST_PER_QUESTION_USD", "0.50"))
LEDGER = pathlib.Path(os.environ.get("BUDGET_LEDGER", str(REPO / ".budget_ledger.json")))

# $ per 1M tokens (input, output). Prefix-matched against the model id.
# Unknown models fall back to a deliberately expensive guess (over-counts = safe).
PRICES = {
    "claude-opus": (5.00, 25.00),
    "claude-sonnet": (3.00, 15.00),
    "claude-haiku": (1.00, 5.00),
    "gemini-2.5-flash": (0.30, 2.50),
    "gemini": (1.25, 10.00),
    "grok": (3.00, 15.00),
    "": (5.00, 25.00),  # fallback
}


def _price(model):
    for prefix in sorted(PRICES, key=len, reverse=True):
        if model.startswith(prefix):
            return PRICES[prefix]
    return PRICES[""]


def _ledger_load():
    import datetime
    month = datetime.date.today().strftime("%Y-%m")
    try:
        data = json.loads(LEDGER.read_text())
    except Exception:
        data = {}
    return data, month


def budget_spent():
    data, month = _ledger_load()
    return float(data.get(month, 0.0))


def budget_add(usage, model):
    """Record cost from a response's usage object. Counts all prompt tokens at the
    full input rate (ignores cache discounts) — over-counts, which is the safe side."""
    pin, pout = _price(model)
    cost = (usage.prompt_tokens / 1e6) * pin + (usage.completion_tokens / 1e6) * pout
    data, month = _ledger_load()
    data[month] = float(data.get(month, 0.0)) + cost
    LEDGER.write_text(json.dumps(data))
    return cost


def budget_check(question_cost=0.0):
    spent = budget_spent()
    if spent >= MONTHLY_BUDGET_USD:
        raise SystemExit(
            f"BUDGET CAP: ${spent:.2f} of ${MONTHLY_BUDGET_USD:.2f} spent this month. "
            f"Raise MONTHLY_BUDGET_USD or wait for the new month."
        )
    if question_cost >= MAX_COST_PER_QUESTION_USD:
        raise RuntimeError(
            f"Question aborted at ${question_cost:.2f} (MAX_COST_PER_QUESTION_USD="
            f"${MAX_COST_PER_QUESTION_USD:.2f}) — likely a runaway exploration."
        )

# --- System prompt: the behavioral contract + the map -------------------------
# AGENTS.md is the vendor-neutral contract; README.md is the narrative schema map.
SYSTEM = (
    (REPO / "AGENTS.md").read_text()
    + "\n\n# Database map\n\n"
    + (REPO / "docs" / "database" / "README.md").read_text()
    + "\n\nYou have a `run_sql` tool. Use it to answer data questions with real rows. "
      "Only SELECT. Always filter big tables by event_id/captured_at and add LIMIT. "
      "If you need exact columns, query information_schema or read them from the map."
)

# --- The one tool Grok gets ---------------------------------------------------
TOOLS = [{
    "type": "function",
    "function": {
        "name": "run_sql",
        "description": "Run a read-only SQL SELECT against the Terminal .5 Postgres "
                       "database and return up to 200 rows as JSON.",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "A single read-only SQL SELECT statement."}
            },
            "required": ["query"],
        },
    },
}]

# Optional live-context tools (Wikipedia / Reddit / TSA flight trends).
# If live_context_tools.py is present and `requests` is installed, the agent
# gets them automatically; otherwise it runs SQL-only.
try:
    from live_context_tools import LIVE_TOOLS, DISPATCH as LIVE_DISPATCH
    TOOLS = TOOLS + LIVE_TOOLS
except ImportError:
    LIVE_DISPATCH = {}

# Defense in depth: the data_reader role is already SELECT-only at the database,
# and we also open a read-only transaction below. This guard just fails fast on
# obviously non-read queries so Grok gets a clear error instead of a DB rejection.
_BLOCK = re.compile(r"\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|copy|call)\b", re.I)

_conn = psycopg.connect(DATABASE_URL, autocommit=True)
_conn.execute("SET default_transaction_read_only = on")
_conn.execute("SET statement_timeout = '30s'")


def run_sql(query: str) -> str:
    q = query.strip().rstrip(";")
    if _BLOCK.search(q) or not re.match(r"(?is)^\s*(with|select)\b", q):
        return json.dumps({"error": "Only read-only SELECT/WITH queries are allowed."})
    try:
        with _conn.cursor() as cur:
            cur.execute(q)
            cols = [d.name for d in cur.description]
            rows = [dict(zip(cols, r)) for r in cur.fetchmany(200)]
        return json.dumps(rows, default=str)
    except Exception as e:  # surface the DB error back to Grok so it can self-correct
        return json.dumps({"error": str(e)})


def ask(question: str) -> str:
    client = OpenAI(api_key=API_KEY, base_url=BASE_URL)
    messages = [{"role": "system", "content": SYSTEM},
                {"role": "user", "content": question}]
    question_cost = 0.0
    for _ in range(12):  # cap tool-call rounds
        budget_check(question_cost)  # hard-stop on monthly cap or runaway question
        resp = client.chat.completions.create(model=MODEL, messages=messages, tools=TOOLS)
        if resp.usage:
            question_cost += budget_add(resp.usage, MODEL)
        msg = resp.choices[0].message
        if not msg.tool_calls:
            return msg.content or ""
        messages.append({
            "role": "assistant",
            "content": msg.content or "",
            "tool_calls": [{
                "id": tc.id, "type": "function",
                "function": {"name": tc.function.name, "arguments": tc.function.arguments},
            } for tc in msg.tool_calls],
        })
        for tc in msg.tool_calls:
            args = json.loads(tc.function.arguments or "{}")
            if tc.function.name == "run_sql":
                result = run_sql(args.get("query", ""))
            elif tc.function.name in LIVE_DISPATCH:
                result = LIVE_DISPATCH[tc.function.name](args)
            else:
                result = json.dumps({"error": f"unknown tool {tc.function.name}"})
            print(f"  ↳ {tc.function.name}: {str(args)[:120]}", file=sys.stderr)
            messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
    return "(stopped after too many tool-call rounds)"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: python examples/grok_sql_agent.py \"<your question>\"")
    print(ask(" ".join(sys.argv[1:])))
