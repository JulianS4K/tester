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

### A create silently failed

The first Unwind-yoga create returned a complete success payload, id and
all, for an event that did not exist -- a follow-up `get_event` on that id
returned "could not be found". Recreating it worked. **Verify every create
with a read**; the success response alone does not prove the write landed.

### Already on Pending, found while verifying

Not from this sweep and not previously surfaced in this work, but they
change the November picture: The Immortal Ball (Sat Oct 17, Masquerade),
Clayface film (Oct 24), **She Wants Revenge + Rosegarden Funeral Party
(Mon Nov 2, Webster Hall)** and **Boy Harsher (Thu Nov 5, Knockdown)**.
November is better supplied than earlier notes in this repo claim.
