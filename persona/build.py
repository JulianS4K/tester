#!/usr/bin/env python3
"""
Compile the persona into the prompt a given surface needs.

The weekly routine's fired sessions have no repo access, so the prompt has to
arrive as one self-contained blob. This directory is the source of truth; the
trigger holds a compiled artifact. Author here, render, push the output.

  build.py list                      profiles and the sections they compose
  build.py render <profile>          the composed prompt, to stdout
  build.py check                     referenced sections exist; none orphaned
  build.py diff <profile> <file>     compare a render against what is live
"""

import argparse
import difflib
import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent
SECTIONS = ROOT / "sections"
ROLES = ROOT / "roles"
CONFIG = ROOT / "profiles.yml"


def load_config():
    return yaml.safe_load(CONFIG.open())


def render(profile_name, config=None):
    config = config or load_config()
    profiles = config["profiles"]
    if profile_name not in profiles:
        sys.exit(f"unknown profile '{profile_name}'. Known: {', '.join(profiles)}")
    profile = profiles[profile_name]

    role_path = ROLES / f"{profile['role']}.md"
    if not role_path.exists():
        sys.exit(f"profile '{profile_name}' names role '{profile['role']}' but "
                 f"{role_path} does not exist")

    parts = [role_path.read_text().strip()]
    for name in profile["sections"]:
        p = SECTIONS / f"{name}.md"
        if not p.exists():
            sys.exit(f"profile '{profile_name}' names section '{name}' but "
                     f"{p} does not exist")
        parts.append(p.read_text().strip())
    # One blank line between blocks, trailing newline at the end -- stable
    # output so a diff against the live prompt shows real changes only.
    return "\n\n".join(parts) + "\n"


def cmd_list(args):
    config = load_config()
    volatile = set(config.get("volatile", []))
    for name, profile in config["profiles"].items():
        text = render(name, config)
        print(f"{name}  ({len(text):,} chars, ~{len(text)//4:,} tokens)")
        desc = " ".join((profile.get("description") or "").split())
        if desc:
            print(f"  {desc}")
        print(f"  role: {profile['role']}")
        for s in profile["sections"]:
            print(f"    {s}{'  [volatile]' if s in volatile else ''}")
        print()
    return 0


def cmd_render(args):
    sys.stdout.write(render(args.profile))
    return 0


def cmd_check(args):
    config = load_config()
    referenced, problems = set(), []

    for name, profile in config["profiles"].items():
        if not (ROLES / f"{profile['role']}.md").exists():
            problems.append(f"{name}: missing role '{profile['role']}'")
        for s in profile["sections"]:
            referenced.add(s)
            if not (SECTIONS / f"{s}.md").exists():
                problems.append(f"{name}: missing section '{s}'")
        if len(profile["sections"]) != len(set(profile["sections"])):
            problems.append(f"{name}: lists a section twice")

    # A section nothing composes is dead weight -- worth surfacing, since the
    # whole point is that the composed prompt is auditable.
    on_disk = {p.stem for p in SECTIONS.glob("*.md")}
    for orphan in sorted(on_disk - referenced):
        problems.append(f"section '{orphan}' exists but no profile uses it")

    for v in config.get("volatile", []):
        if v not in on_disk:
            problems.append(f"volatile list names '{v}' which is not on disk")

    print(f"check: {len(config['profiles'])} profile(s), {len(on_disk)} section(s)")
    for p in problems:
        print(f"  PROBLEM {p}")
    return 1 if problems else 0


def cmd_diff(args):
    live = pathlib.Path(args.live).read_text()
    built = render(args.profile)
    if live.strip() == built.strip():
        print(f"diff: {args.profile} matches {args.live} exactly")
        return 0
    delta = list(difflib.unified_diff(
        live.splitlines(), built.splitlines(),
        fromfile=f"live ({args.live})", tofile=f"built ({args.profile})",
        lineterm="", n=1,
    ))
    print(f"diff: {len(delta)} line(s) differ\n" + "\n".join(delta[:args.max_lines]))
    if len(delta) > args.max_lines:
        print(f"... {len(delta) - args.max_lines} more")
    return 1


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list").set_defaults(func=cmd_list)

    r = sub.add_parser("render")
    r.add_argument("profile")
    r.set_defaults(func=cmd_render)

    sub.add_parser("check").set_defaults(func=cmd_check)

    d = sub.add_parser("diff")
    d.add_argument("profile")
    d.add_argument("live")
    d.add_argument("--max-lines", type=int, default=80)
    d.set_defaults(func=cmd_diff)

    a = p.parse_args()
    sys.exit(a.func(a) or 0)


if __name__ == "__main__":
    main()
