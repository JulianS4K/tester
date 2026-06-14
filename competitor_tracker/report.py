"""Render collected signals as Markdown and a console summary."""

from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timezone

from .models import Signal


def _fmt_date(dt: datetime | None) -> str:
    if not dt:
        return "—"
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%d")


def to_markdown(signals: list[Signal], product_names: dict[str, str]) -> str:
    """Build a Markdown report grouped by product, then competitor."""
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        "# Competitor Tracking Report",
        "",
        f"_Generated {now} — {len(signals)} new signal(s)._",
        "",
    ]

    if not signals:
        lines.append("No new signals since the last run. 🎉")
        return "\n".join(lines) + "\n"

    by_product: dict[str, dict[str, list[Signal]]] = defaultdict(lambda: defaultdict(list))
    for s in signals:
        by_product[s.product_key][s.competitor].append(s)

    for product_key, competitors in by_product.items():
        lines.append(f"## {product_names.get(product_key, product_key)}")
        lines.append("")
        for competitor, items in competitors.items():
            lines.append(f"### {competitor}")
            lines.append("")
            for s in sorted(items, key=lambda x: x.sort_key(), reverse=True):
                label = "📰" if s.kind == "article" else "🌐"
                title = f"[{s.title}]({s.url})" if s.url else s.title
                meta = " · ".join(filter(None, [_fmt_date(s.published), s.summary]))
                lines.append(f"- {label} {title}" + (f"  \n  _{meta}_" if meta else ""))
            lines.append("")

    return "\n".join(lines) + "\n"


def print_summary(signals: list[Signal], product_names: dict[str, str]) -> None:
    """Print a compact, human-readable summary to stdout."""
    print("\n" + "=" * 56)
    print(f"  {len(signals)} new signal(s)")
    print("=" * 56)
    if not signals:
        print("  Nothing new since the last run.")
        return

    counts: dict[str, int] = {}
    for s in signals:
        counts[s.competitor] = counts.get(s.competitor, 0) + 1
    for competitor, n in sorted(counts.items(), key=lambda kv: kv[1], reverse=True):
        print(f"  {n:>3}  {competitor}")
