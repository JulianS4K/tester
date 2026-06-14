"""Command-line interface for the competitor tracker.

Examples:
    python -m competitor_tracker track
    python -m competitor_tracker track --product d4 --lookback-days 14
    python -m competitor_tracker list
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .config import Settings
from .report import print_summary, to_markdown
from .storage import Store
from .tracker import Tracker

DEFAULT_CONFIG = "config.yaml"
DEFAULT_STATE = "data/state.json"
DEFAULT_REPORT = "reports/latest.md"


def _cmd_list(settings: Settings) -> int:
    for product in settings.products.values():
        print(f"\n{product.name} ({product.key}) — {len(product.competitors)} competitor(s)")
        for c in product.competitors:
            extras = []
            if c.website:
                extras.append(c.website)
            if c.tags:
                extras.append("[" + ", ".join(c.tags) + "]")
            suffix = ("  " + " ".join(extras)) if extras else ""
            print(f"  - {c.name}{suffix}")
    return 0


def _cmd_track(settings: Settings, args) -> int:
    products = settings.select_products(args.product)
    if args.lookback_days is not None:
        settings.lookback_days = args.lookback_days
    if args.sources:
        settings.sources = args.sources

    store = Store(args.state)
    tracker = Tracker(settings, store)
    try:
        signals = tracker.run(products)
    finally:
        tracker.close()

    names = {p.key: p.name for p in settings.products.values()}
    print_summary(signals, names)

    report = to_markdown(signals, names)
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report)
    print(f"\nReport written to {out}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="competitor_tracker",
        description="Track major competitors of the D1 and D4 product lines.",
    )
    parser.add_argument("--config", default=DEFAULT_CONFIG, help="Path to config.yaml")

    sub = parser.add_subparsers(dest="command", required=True)

    p_track = sub.add_parser("track", help="Fetch new competitor signals")
    p_track.add_argument(
        "--product", action="append",
        help="Limit to a product key (repeatable). Default: all.",
    )
    p_track.add_argument(
        "--lookback-days", type=int, default=None,
        help="Only report news from the last N days (overrides config).",
    )
    p_track.add_argument(
        "--source", dest="sources", action="append",
        help="Limit to a source: news, website (repeatable).",
    )
    p_track.add_argument("--state", default=DEFAULT_STATE, help="Path to state file")
    p_track.add_argument("--output", default=DEFAULT_REPORT, help="Markdown report path")

    sub.add_parser("list", help="List configured products and competitors")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        settings = Settings.load(args.config)
    except (FileNotFoundError, ValueError) as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 2

    if args.command == "list":
        return _cmd_list(settings)
    if args.command == "track":
        return _cmd_track(settings, args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
