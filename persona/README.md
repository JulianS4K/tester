# Persona

The definition of Julian's assistant, split into composable sections and
compiled into whatever a given surface needs.

## Why a build step

The weekly routine's fired sessions have **no repo access** — the trigger is
configured with no sources, so nothing can read a file at runtime. The prompt
has to arrive as one self-contained blob.

So this directory is the source of truth and the trigger prompt is a *compiled
artifact*. Author here, render, push the output to the trigger. The alternative
— editing a 6,000-word string inside a scheduler — is what this replaces: not
versioned, not diffable, not reusable, and impossible to review.

```bash
python3 persona/build.py list                 # profiles and sections
python3 persona/build.py render weekly-brief  # the text to push to the trigger
python3 persona/build.py check                # referenced sections all exist
python3 persona/build.py diff weekly-brief current.txt   # against what is live
```

## Layout

| Path | What it is |
|---|---|
| `roles/<profile>.md` | The opening framing — differs per surface |
| `sections/NN-<name>.md` | Shared content, ordered by filename |
| `profiles.yml` | Which role + sections compose into which surface |
| `build.py` | The compiler |

Sections are numbered so the composed order is obvious from the directory
listing, and `profiles.yml` names them explicitly rather than globbing — a
profile that silently gains a section because someone added a file is exactly
the drift this is meant to prevent.

## Profiles

- **`weekly-brief`** — everything, including the report structure. This is what
  the Sunday routine runs on.
- **`chat`** — same identity, taste, strategy, rules and data landmines, minus
  the report spec. For an on-demand surface that answers questions rather than
  filing a weekly report.
- **`minimal`** — identity and taste only. For a lightweight surface where the
  full operating manual is overkill.

## Volatile vs durable

`70-known-items.md` and `80-extra-context.md` are the volatile sections — dated
facts that go stale. Everything else is durable. Keeping them as separate files
means a stale trip itinerary can be edited without touching the taste profile,
and a reviewer can see at a glance which is which.
