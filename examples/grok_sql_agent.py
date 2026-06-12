#!/usr/bin/env python3
"""
Grok -> Terminal .5 database, read-only.

A minimal reference agent that lets Grok (xAI) answer questions about the data by
writing and running read-only SQL. The pattern is model-agnostic: it's plain
OpenAI-compatible function calling, so the same shape works for GPT, etc.

  Grok decides what to query  ->  calls the run_sql tool  ->  we execute it against
  the read-only Postgres role  ->  Grok interprets the rows and answers.

Setup:
    pip install "openai>=1.0" "psycopg[binary]>=3.1"
    export XAI_API_KEY="xai-..."                       # from console.x.ai
    export DATABASE_URL="postgresql://data_reader:PW@db.hzrizjeaxlqcxfrtczpq.supabase.co:5432/postgres?sslmode=require"
    # optional: export XAI_MODEL="grok-4"

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

XAI_MODEL = os.environ.get("XAI_MODEL", "grok-4")
DATABASE_URL = os.environ["DATABASE_URL"]
REPO = pathlib.Path(__file__).resolve().parents[1]

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
    client = OpenAI(api_key=os.environ["XAI_API_KEY"], base_url="https://api.x.ai/v1")
    messages = [{"role": "system", "content": SYSTEM},
                {"role": "user", "content": question}]
    for _ in range(12):  # cap tool-call rounds
        resp = client.chat.completions.create(model=XAI_MODEL, messages=messages, tools=TOOLS)
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
            result = run_sql(args.get("query", "")) if tc.function.name == "run_sql" else "{}"
            print(f"  ↳ run_sql: {args.get('query','')[:120]}", file=sys.stderr)
            messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
    return "(stopped after too many tool-call rounds)"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: python examples/grok_sql_agent.py \"<your question>\"")
    print(ask(" ".join(sys.argv[1:])))
