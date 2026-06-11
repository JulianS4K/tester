# Access & Connection (read-only)

The safest way to hand someone (or their bot) access to this data is a **dedicated
database-enforced read-only role**. Because the restriction lives in Postgres, the agent
*physically cannot* write, modify, or drop anything — regardless of which client, language, or
model it uses. This is the recommended setup.

- **Project ref:** `hzrizjeaxlqcxfrtczpq`
- **Direct host:** `db.hzrizjeaxlqcxfrtczpq.supabase.co` · port `5432` · database `postgres`
- **API URL:** `https://hzrizjeaxlqcxfrtczpq.supabase.co`

---

## 1. Create the read-only role (run once, as an admin)

Run this in the **Supabase dashboard → SQL Editor** (or via a migration). Choose a strong
password and keep it out of the repo.

```sql
-- A login role that can only read.
create role data_reader with login password 'CHOOSE_A_STRONG_PASSWORD';

-- Connect + see the schema, read every existing table/view.
grant connect on database postgres to data_reader;
grant usage on schema public to data_reader;
grant select on all tables in schema public to data_reader;

-- Make future tables readable automatically.
alter default privileges in schema public grant select on tables to data_reader;

-- Belt-and-suspenders: ensure no write grants ever leak in.
revoke insert, update, delete, truncate on all tables in schema public from data_reader;
```

> A direct Postgres role bypasses Row-Level Security and sees **all rows** — that's expected
> and fine here, because the role is `SELECT`-only. The protection is the missing write grants,
> not RLS. Only grant this role to a trusted reader; it can read everything in `public`.

### Optional: cap how hard the reader can hit the DB
```sql
alter role data_reader set statement_timeout = '30s';
alter role data_reader set idle_in_transaction_session_timeout = '60s';
```

---

## 2. Connection string to hand out

```
postgresql://data_reader:CHOOSE_A_STRONG_PASSWORD@db.hzrizjeaxlqcxfrtczpq.supabase.co:5432/postgres?sslmode=require
```

For pooled / serverless clients, use the **connection pooler** string instead — copy it from
**Supabase → Project Settings → Database → Connection string (Transaction/Session pooler)** and
swap in the `data_reader` user + password. SSL is required.

This string works with any client: `psql`, `psycopg`/`asyncpg` (Python), `pg` (Node),
`pgx` (Go), JDBC, etc. Give the agent a single tool that runs a query against it and returns rows.

---

## 3. MCP option (Claude Desktop / Code / Cursor)

Agents that speak MCP can use the **Supabase MCP server in read-only mode** instead of a raw
connection — start it with the `--read-only` flag and a Personal Access Token. Example
`mcp.json` entry:

```json
{
  "mcpServers": {
    "supabase": {
      "command": "npx",
      "args": [
        "-y", "@supabase/mcp-server-supabase@latest",
        "--read-only",
        "--project-ref=hzrizjeaxlqcxfrtczpq"
      ],
      "env": { "SUPABASE_ACCESS_TOKEN": "<a read-only-scoped PAT>" }
    }
  }
}
```

`--read-only` makes the MCP server execute SQL as a read-only Postgres user, so writes are
rejected. For non-MCP agents (e.g. Grok via API), use the connection string from §2 instead.

---

## 4. Security notes

- **Never share the `service_role` key or the project's database owner password** with a
  reader or a bot — those bypass every safeguard. Hand out only the `data_reader` credentials.
- **Two tables currently have Row-Level Security disabled** — `public.sg_market_chart` and
  `public.axs_probe`. This only matters for the **anon/publishable API key** path (PostgREST):
  anyone with the anon key can read those two tables. It does **not** affect the `data_reader`
  role approach above. If you also expose the anon key, consider enabling RLS (add policies
  first, or this will block all access):
  ```sql
  alter table public.sg_market_chart enable row level security;
  alter table public.axs_probe enable row level security;
  ```
- Rotate the `data_reader` password if it leaks: `alter role data_reader with password '...';`
- To revoke access entirely: `drop role data_reader;` (disconnect its sessions first).
