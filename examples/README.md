# Examples — plugging an agent into the data

Reference clients that let a model answer questions about the data by running read-only SQL.
These are integration helpers (run by you, the operator), not part of the data surface itself.

## `grok_sql_agent.py` — provider-agnostic read-only SQL agent (defaults to Sonnet)

One script, any model. It uses OpenAI-compatible function calling, so the **same code** drives
Claude Sonnet (default), Grok, Gemini, GPT, or a local model — you change three env vars. It
gives the model a `run_sql` tool wired to the read-only `data_reader` role (plus the optional
live-context tools), with `AGENTS.md` + the schema map as its system prompt. The model writes
SQL, the script runs it read-only, the model interprets the rows.

It also enforces a **client-side budget guard** (`MONTHLY_BUDGET_USD`, default **$20**) and a
per-question runaway cap — see "Budget" below. For a multi-user web UI, see
[`../docs/server-setup.md`](../docs/server-setup.md).

### 1. Prereqs
- The read-only `data_reader` role exists (see [`../docs/database/access.md`](../docs/database/access.md)).
- A provider API key (Anthropic by default).

```bash
pip install "openai>=1.0" "psycopg[binary]>=3.1"
export AGENT_API_KEY="sk-ant-..."   # provider key (Anthropic by default)
export DATABASE_URL="postgresql://data_reader:YOUR_PW@db.hzrizjeaxlqcxfrtczpq.supabase.co:5432/postgres?sslmode=require"
```

Switch providers with two env vars:

| Provider | `AGENT_BASE_URL` | `AGENT_MODEL` |
|---|---|---|
| **Claude (default)** | `https://api.anthropic.com/v1/` | `claude-sonnet-4-6` |
| Grok | `https://api.x.ai/v1` | `grok-4` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai/` | `gemini-2.5-flash` |
| Local (Ollama) | `http://localhost:11434/v1` | `qwen3:32b` |

### 2. Run
```bash
python examples/grok_sql_agent.py "Top 5 upcoming events by get-in price"
python examples/grok_sql_agent.py "Report on the next Knicks home game"
```

Tool calls are echoed to stderr so you can see exactly what the model queried.

### Budget
A local ledger (`.budget_ledger.json`, gitignored) tracks estimated monthly spend and the
script **refuses to run once `MONTHLY_BUDGET_USD` (default $20) is hit**; a single runaway
question is killed at `MAX_COST_PER_QUESTION_USD` (default $0.50). Costs are over-counted (cache
discounts ignored) so the cap errs safe. **Also set a hard spend limit in the provider console**
(a dedicated capped workspace) as the unbypassable backstop — the local ledger is the graceful
in-month stop, not the only wall.

### Why it's safe
Three layers of read-only: the `data_reader` role has only `SELECT` granted, the connection
sets `default_transaction_read_only = on`, and the script rejects anything that isn't a
`SELECT`/`WITH`. There is no path to write.

### Adapting to other models
It's vanilla OpenAI-compatible function calling — point `base_url`/key at OpenAI, Together,
etc. and reuse the same `run_sql` tool and system prompt. For Claude, prefer the MCP path
([`../.mcp.json`](../.mcp.json)) instead.
