# Self-hosted server — multi-user chat over the data (Sonnet, $20-capped)

Stand up a shared, multi-user chat UI for the database on a cheap VPS (~$10–20/mo).
**Open WebUI** is the front end (logins, history, model picker); **LiteLLM** is a thin
proxy in front of the model providers that enforces a hard **$20/month budget** and lets you
swap or add models without touching the UI. Your read-only Postgres role and the same
`run_sql` + live-context tools plug in underneath.

```
 users ──► Open WebUI ──► LiteLLM proxy ($20 cap, routing) ──► Anthropic (Sonnet)
   (logins, history)        (key custody, budget)          └─► (later: Haiku / Gemini / local)
                                   │
                         run_sql + live tools ──► data_reader (read-only Postgres)
```

Everything here is open source; the only paid piece is the model API behind the capped proxy.

---

## 1. `docker-compose.yml`

```yaml
services:
  litellm:
    image: ghcr.io/berriai/litellm:main-latest
    command: ["--config", "/app/config.yaml"]
    volumes:
      - ./litellm.config.yaml:/app/config.yaml:ro
    environment:
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}      # key from your $20-capped workspace
      LITELLM_MASTER_KEY: ${LITELLM_MASTER_KEY}     # any strong secret; Open WebUI uses it
    ports: ["4000:4000"]
    restart: unless-stopped

  open-webui:
    image: ghcr.io/open-webui/open-webui:main
    depends_on: [litellm]
    environment:
      OPENAI_API_BASE_URL: http://litellm:4000/v1
      OPENAI_API_KEY: ${LITELLM_MASTER_KEY}
      WEBUI_AUTH: "true"                             # require logins
      ENABLE_SIGNUP: "false"                         # you invite users; no open signup
    volumes:
      - openwebui:/app/backend/data
    ports: ["3000:3000"]                             # the URL you hand teammates
    restart: unless-stopped

volumes:
  openwebui:
```

## 2. `litellm.config.yaml` — where the $20 cap lives

```yaml
model_list:
  - model_name: sonnet                  # what users see in the picker
    litellm_params:
      model: anthropic/claude-sonnet-4-6
      api_key: os.environ/ANTHROPIC_API_KEY
  # Add later — same proxy, same cap:
  # - model_name: haiku
  #   litellm_params: { model: anthropic/claude-haiku-4-5, api_key: os.environ/ANTHROPIC_API_KEY }
  # - model_name: gemini-flash
  #   litellm_params: { model: gemini/gemini-2.5-flash, api_key: os.environ/GEMINI_API_KEY }

litellm_settings:
  max_budget: 20            # HARD cap, USD — proxy refuses calls past this
  budget_duration: 30d      # rolling 30-day window
  success_callback: []      # add langfuse/db logging here if you want per-user spend
```

## 3. Run it

```bash
export ANTHROPIC_API_KEY="sk-ant-..."         # from the capped workspace (step below)
export LITELLM_MASTER_KEY="$(openssl rand -hex 24)"
docker compose up -d
# Open http://<server-ip>:3000 — first account becomes admin; invite users from Settings.
```

---

## Two budget walls (belt and suspenders)

1. **Provider workspace cap (do this first, unbypassable):** in the Anthropic Console create a
   dedicated **Workspace**, set its **monthly spend limit to $20**, and issue the key *from that
   workspace*. No bug in the proxy or UI can spend past it.
2. **LiteLLM `max_budget: 20`** — the proxy stops calling *before* the provider wall and returns
   a clean "budget exceeded" to users instead of raw API errors. Set it equal to or just under
   the workspace cap.

Per-user limits: give each Open WebUI user their own LiteLLM virtual key with its own
`max_budget` (e.g. $5 each) so one person can't drain the pool — see LiteLLM's "virtual keys".

---

## Wiring the data tools into the chat

Open WebUI calls tools two ways — pick one:

- **MCP (simplest):** point Open WebUI at the read-only Supabase MCP server from
  [`../../.mcp.json`](../../.mcp.json) (Settings → Tools/Connections). The model gets `run_sql`
  equivalent out of the box.
- **Custom tools:** add `run_sql` + the live-context fetchers
  ([`../../examples/live_context_tools.py`](../../examples/live_context_tools.py)) as Open WebUI
  "tools" (Python), backed by the `data_reader` connection string. Same functions the CLI agent uses.

Either way, paste the contents of [`../../AGENTS.md`](../../AGENTS.md) into the model's **system
prompt** field in Open WebUI so the report conventions and the untrusted-content rule apply.

---

## When to graduate to local models

This server is model-agnostic by design: to move a workload off paid APIs later, add an Ollama/
vLLM entry to `litellm.config.yaml` (`model: ollama/qwen3:32b`, `api_base: http://ollama:11434`)
and it appears in the same picker. Worth it only when privacy demands it or volume outgrows API
pricing — see the cost comparison in the PR discussion. The harness doesn't change.
