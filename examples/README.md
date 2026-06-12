# Examples — plugging an agent into the data

Reference clients that let a model answer questions about the data by running read-only SQL.
These are integration helpers (run by you, the operator), not part of the data surface itself.

## `grok_sql_agent.py` — Grok (xAI), read-only

Grok doesn't use MCP like Claude Desktop/Cursor; you connect it through the **xAI API's
function-calling** (OpenAI-compatible). This script gives Grok one `run_sql` tool wired to the
read-only `data_reader` Postgres role, with `AGENTS.md` + the schema map as its system prompt.
Grok writes the SQL, the script runs it read-only, Grok interprets the rows.

### 1. Prereqs
- The read-only `data_reader` role exists (see [`../docs/database/access.md`](../docs/database/access.md)).
- An xAI API key from <https://console.x.ai>.

```bash
pip install "openai>=1.0" "psycopg[binary]>=3.1"
export XAI_API_KEY="xai-..."
export DATABASE_URL="postgresql://data_reader:YOUR_PW@db.hzrizjeaxlqcxfrtczpq.supabase.co:5432/postgres?sslmode=require"
# optional: export XAI_MODEL="grok-4"   # set to the current Grok model
```

### 2. Run
```bash
python examples/grok_sql_agent.py "Top 5 upcoming events by get-in price"
python examples/grok_sql_agent.py "How many SeatGeek listings did we capture in the last 24h?"
```

`run_sql` calls are echoed to stderr so you can see exactly what Grok queried.

### Why it's safe
Three layers of read-only: the `data_reader` role has only `SELECT` granted, the connection
sets `default_transaction_read_only = on`, and the script rejects anything that isn't a
`SELECT`/`WITH`. There is no path to write.

### Adapting to other models
It's vanilla OpenAI-compatible function calling — point `base_url`/key at OpenAI, Together,
etc. and reuse the same `run_sql` tool and system prompt. For Claude, prefer the MCP path
([`../.mcp.json`](../.mcp.json)) instead.
