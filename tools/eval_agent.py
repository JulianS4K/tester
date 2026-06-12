#!/usr/bin/env python3
"""
Eval harness — golden trap-questions scored against live ground truth (limit #1).

Each case in tools/evals.json asks the agent a question whose correct answer is
derived AT RUN TIME from the database (`truth_sql`), so cases stay valid as data
changes. Assertions check the agent's prose against that truth:

  contains_truth        truth column's value must appear in the answer
  numeric_close         some number in the answer within pct of the truth value
  contains_any          one of the static strings must appear ("warn" = report only)
  conditional_contains  if the truth row contains X, the answer must mention one of [...]

Run (needs DATABASE_URL + the agent's provider key, same env as the agent itself):

    python tools/eval_agent.py             # all cases
    python tools/eval_agent.py home_game   # cases whose id contains "home_game"

Exit code = number of failed required assertions. Run after model/prompt changes
and before switching default models; track the score over time.
"""
import os
import re
import sys
import json
import pathlib

import psycopg

REPO = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / "examples"))

from grok_sql_agent import ask, MODEL  # noqa: E402  (reuses the agent under test)

_conn = psycopg.connect(os.environ["DATABASE_URL"])
_conn.execute("SET default_transaction_read_only = on")


def q(sql):
    with _conn.cursor() as cur:
        cur.execute(sql)
        cols = [d.name for d in cur.description]
        row = cur.fetchone()
        return dict(zip(cols, row)) if row else None


def numbers_in(text):
    return [float(m.replace(",", "")) for m in re.findall(r"\$?([\d,]+(?:\.\d+)?)", text)]


def check(a, answer, truth):
    ans = answer.lower()
    t = a["type"]
    if t == "contains_truth":
        v = str(truth.get(a["column"], "")).strip()
        return bool(v) and v.lower() in ans, f"answer must contain {a['column']}={v!r}"
    if t == "numeric_close":
        v = float(truth[a["column"]])
        ok = any(abs(n - v) <= a["pct"] * max(abs(v), 1e-9) for n in numbers_in(answer))
        return ok, f"a number within {a['pct']:.0%} of {v} must appear"
    if t == "contains_any":
        ok = any(s.lower() in ans for s in a["values"])
        return ok, f"answer must mention one of {a['values']}"
    if t == "conditional_contains":
        trigger = str(truth.get(a["when_column"], ""))
        if a["when_substr"].lower() not in trigger.lower():
            return True, "(condition not triggered)"
        ok = any(s.lower() in ans for s in a["values"])
        return ok, f"truth contains {a['when_substr']!r} -> answer must mention one of {a['values']}"
    return False, f"unknown assert type {t}"


def main():
    flt = sys.argv[1] if len(sys.argv) > 1 else ""
    cases = [c for c in json.loads((REPO / "tools" / "evals.json").read_text())
             if flt in c["id"]]
    failures = warns = 0

    for c in cases:
        params = q(c["setup_sql"]) if c.get("setup_sql") else {}
        if c.get("setup_sql") and not params:
            print(f"~ {c['id']}: SKIP (setup_sql matched no rows right now)")
            continue
        question = c["question"].format(**params)
        truth = q(c["truth_sql"].format(**params)) or {}
        print(f"\n=== {c['id']} [{MODEL}]\n  Q: {question}")
        try:
            answer = ask(question)
        except Exception as e:
            print(f"  ✗ agent error: {e}")
            failures += len([a for a in c["asserts"] if a.get("level") != "warn"])
            continue
        print(f"  A: {answer[:300].replace(chr(10), ' ')}…")
        for a in c["asserts"]:
            ok, desc = check(a, answer, truth)
            level = a.get("level", "required")
            mark = "✓" if ok else ("⚠" if level == "warn" else "✗")
            print(f"  {mark} {desc}")
            if not ok:
                if level == "warn":
                    warns += 1
                else:
                    failures += 1

    print(f"\n{'-' * 50}\nresult: {failures} failed, {warns} warnings, model={MODEL}")
    sys.exit(min(failures, 125))


if __name__ == "__main__":
    main()
