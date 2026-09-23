# Q4 candidates from the inbox — kink/community slot

Read from Gmail 2026-09-23: 33 Eventbrite "Just added!" threads from the three
organizers that feed this slot. No API, no credits, no egress — the mail was
already there. Nothing below is on a calendar; these are candidates.

## Dated and upcoming

| Date | Event | Organizer | Note |
|---|---|---|---|
| Mon Sep 28 | Unwind: Stretch & Relax Yoga | Pagans Paradise | physical **and** community in one slot; WellnessLiving yoga does not start until Oct 5, so this covers the gap |
| Fri Oct 2, 8pm | Dynamic Tantric Dance Release | Pagans Paradise | |
| Mon Oct 5 | HMU Academy: Mean Toys, Fine Touch | Hit Me Up | Learning + kink |
| Tue Oct 6 | The Art of Nipple Pleasure | Miss Bloom | Tuesday = boxing |
| Sat Oct 10 | COS/PL@YA Cosplay Kinky Party | Pagans Paradise | clashes with Clown Cult |
| Sun Oct 18, 7–10pm | Kinky Carnival | Miss Bloom | |
| Mon Oct 19 | HMU Academy: Floggers & Florentine | Hit Me Up | Learning + kink |
| Wed Oct 21 | Poly Connections: Game Night | Pagans Paradise | structured social |
| **Wed Oct 28, 7–10pm** | **Kinky Speed Dating & Friending** | Pagans Paradise | **see below** |
| Tue Nov 3, 7:30pm | The Art of Kissing | Miss Bloom | Tuesday = boxing |
| **Wed Nov 11, 7pm** | **Poly Connections: Karaoke** | Pagans Paradise | **fills the Nov 9-15 empty week** |

## The one that matters most

**Kinky Speed Dating & Friending, Wed Oct 28.** STRATEGY names the ask — the
15-second close — as the bottleneck, and says structured formats beat open
mixers and bars. This is the most structured format available: the format
does the asking. It is also inside a community he is already vetted in, so
it is repeat-context rather than a cold room.

It collides with Devil Master at Saint Vitus the same evening (6pm show,
7pm event). Both are live; that is a menu, not a verdict.

## A pattern worth seeing

**Pagans Paradise runs its structured socials on Wednesdays** — Game Night
Oct 21, Speed Dating Oct 28, Karaoke Nov 11. Wednesday is also the only flex
night left once improv takes Saturday, and improv rehearsal wants it. The
Wednesday slot is now contested and should be spent deliberately rather than
first-come.

## Undated

Announced without a date in the snippet, so each needs its own thread read:
Obsidian Playground (Cornstock, Ember Vessel, Massage Magic), Decolonize Your
Pleasure, ARKHAM ASYLUM (a Saturday), GAGGED FOR BRUNCH (a Sunday), Sacred
Intimacy, Rope and Sensation Play, How to Play Party, Dungeons & Daddy Issues,
Sapphic Massage Magic, K!nky Improv: Fantasy Building & Roleplay, FETISH NYC
Book Launch.

K!nky Improv is worth pricing out — it is the only thing found that sits in
the Learning and kink slots at once while also being improv practice.

## What this settles

The kink/community slot was recorded as running dry after Oct 8. It does not:
there are eleven dated candidates through Nov 11, and the two calendar gaps
this plan had left — the Nov 9-15 empty week and the slot itself — are both
answered from mail that had already arrived.

---

## Scheduled 2026-09-23

Ten created on **Pending** (not The Show — none is ticketed). Written with
`notificationLevel: NONE`, no emojis, VERIFY tags where the mail was silent.

Sep 28 Unwind yoga · Oct 2 Dynamic Tantric Dance · Oct 5 HMU Mean Toys ·
Oct 6 Art of Nipple Pleasure · Oct 10 COS/PLAY A · Oct 18 Kinky Carnival
(VERIFY VENUE) · Oct 19 HMU Floggers · Oct 21 Poly Game Night ·
Oct 28 Kinky Speed Dating · Nov 11 Poly Karaoke.

The eleven source threads were marked read.

### Dropped on inspection

**The Art of Kissing (Nov 3)** is in NEW ORLEANS — "The Twilight Room @ the
AllWays", 7:30pm **CT**. The announcement subject and snippet look identical
to the NYC ones; only the full body carries the city. Miss Bloom runs both
markets, so every Miss Bloom event needs its body read before it is
scheduled. Kinky Carnival is tagged VERIFY VENUE for exactly this reason.

### Descriptions are the source email, nothing else

Each event's description is its Gmail thread link and nothing more. The
first pass put conflict notes there, which was wrong on Julian's own
convention -- conflict analysis does not belong on a calendar, overlaps
are a menu to read at the time. The link gets back to the full listing,
price and address, which prose cannot.

### THREE OF TEN CREATES SILENTLY FAILED

Not a one-off. Unwind yoga, Dynamic Tantric Dance and COS/PLAY A each
returned a complete success payload with an id, and each was absent
afterwards -- two of them even appeared in a `list_events` read at 00:11
and were gone by 00:14, with `update_event` on their ids returning "could
not be found". All three were recreated and verified.

**A write to this calendar is not done until a read confirms it.** The
success response proves nothing, and neither does one subsequent listing.
Anything that automates calendar writes here needs a read-back step, or
it will report ten events created and leave seven.

### Already on Pending, found while verifying

Not from this sweep and not previously surfaced in this work, but they
change the November picture: The Immortal Ball (Sat Oct 17, Masquerade),
Clayface film (Oct 24), **She Wants Revenge + Rosegarden Funeral Party
(Mon Nov 2, Webster Hall)** and **Boy Harsher (Thu Nov 5, Knockdown)**.
November is better supplied than earlier notes in this repo claim.

---

## HMU newsletter cross-check, 2026-09-23

Julian supplied HMU's own upcoming-events list. Checked every date against
the actual weekday; fifteen of seventeen agree, and the two that do not are
both events already on the calendar.

| | Newsletter says | Actually |
|---|---|---|
| Kinky Carnival | Monday, October 18th | **Sunday** Oct 18 |
| HMU Academy: Florentine Flogging | Tuesday, October 19th | **Monday** Oct 19 |

The Eventbrite "Just added" mail gave Sunday Oct 18 and Monday Oct 19 for
these, so what is scheduled is right and the newsletter's day names are
wrong. Two independent sources agreeing on the date is what caught it;
either one alone would have been trusted.

### Resolves the Kinky Carnival VERIFY

It appears in HMU's own newsletter, so it is an HMU event in NYC, not one
of Miss Bloom's New Orleans dates. Retitled and given HMU's address; the
VERIFY VENUE tag is gone.

### Open Loft may be under-scheduled

HMU lists Coworking Open Loft as **every Monday**. The calendar has it
fortnightly — Oct 12, 26, Nov 9, 23, Dec 7, 21. Either HMU changed
frequency or the recurrence was set to every other week by mistake; worth
one look, because it is a standing repeat-context slot and doubling it is
free.

### PilaTEASE answers the physical gap

"HMU PilaTEASE Classes, ongoing dates" is a recurring physical activity
inside the community he is already vetted in. That is the physical slot
and the community slot in one, on an ongoing basis — which is exactly what
the plan needs after boxing ends Dec 15, and better than Sunday League
because it does not land on the rest day.

### Not yet scheduled

Sep 24 Seamless Play · Sep 28 Game & Craft Night · Oct 7 Tie Me Down Rope
Jam · Oct 11 Naked Wrestling · Oct 12 Navigating Play Parties Solo ·
**Oct 13 BIPOC Speed Dating + Mixer** · Oct 14 Gangbangs · Oct 15 Movie
Night: Jennifer's Body · Oct 23 All Hallows Tease · Oct 25 Nips & Bits ·
Oct 27 Embrace Your Kinky Self · Oct 27 FETISH NYC Book Launch.

All twelve were added on Julian's instruction — he will thin them by hand.
The cadence concern stands and is his to weigh: October now holds eighteen
kink/community entries against a rule of one every week or two, so this is
a menu to cut down, not a schedule to work through.

**They are all-day entries tagged VERIFY TIME.** The newsletter publishes
dates only, and none of the twelve has an Eventbrite announcement in the
inbox yet — the newsletter runs ahead of the mail. HMU's times genuinely
vary by format (Academy classes 7-9pm, mixers to 11pm or later, Book Swap
6-8pm, and Naked Yoga Flow at 11am on a Sunday), so there is no safe
default to assume. Each description carries the pattern for its format
without stating it as the event's time. The times arrive by mail later and
the ingest pipeline will carry them.
