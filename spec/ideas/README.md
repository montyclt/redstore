# Ideas

One file per idea. **Nothing in this directory is committed to**: these are things that were
considered, or built and then withdrawn, kept here so the reasoning is not lost and so the same
ground is not re-covered from scratch.

An idea file should say what the thing would do, what it is worth, **what it would cost**, and why
it is not in the mod today. If an idea is ever adopted it moves to `spec/blocks/` and stops being
an idea; if it is ruled out for good it stays here with that written down.

| Idea | Status |
| --- | --- |
| [analog-and-gate.md](analog-and-gate.md) | Built, then withdrawn on 2026-09-20 |
| [analog-or-gate.md](analog-or-gate.md) | Built, then withdrawn on 2026-09-20 |
| [analog-xor-gate.md](analog-xor-gate.md) | Built, then withdrawn on 2026-09-20 |
| [t-flip-flop.md](t-flip-flop.md) | Proposed on 2026-09-21 |
| [rs-latch.md](rs-latch.md) | Proposed on 2026-09-21 |
| [vertical-wire.md](vertical-wire.md) | Proposed on 2026-09-21 |

The three newest are unrelated to the analog gates and to each other, except that two of them —
the [T flip-flop](t-flip-flop.md) and the [RS latch](rs-latch.md) — would both give the mod
**memory**, which it has none of today. That is one decision, not two, and it is taken once.

## Why the three analog gates were withdrawn together

They were a second evaluation mode on every logic gate: instead of treating the inputs as booleans,
the gate would compute on the raw 0–15 strengths. Implemented, working, and removed again for three
reasons.

**Almost all of it duplicates the comparator.** Vanilla already has one block for arithmetic on
signal strengths, and it is a single cell:

| Analog operation | What it costs in vanilla |
| --- | --- |
| `15 − x`, every negated form | **one** comparator in subtract mode with a block of redstone behind it |
| `max(a, b)` — the OR | **nothing**: two dust lines meeting already take the maximum |
| `abs(a − b)` — the XOR | two comparators, or **one** whenever it is known which input is larger |
| `min(a, b)` — the AND | two comparators in series, with one input split into two equal-length branches |

**It taxed everyone.** Carrying the mode meant the click cycle ran through four settings instead of
two, so a player who only ever wanted NAND had to pass through analog to get back, and could land
in it by accident.

**It blurred a line vanilla draws clearly.** Vanilla keeps arithmetic in the comparator and boolean
logic in torches and repeaters. Design rule 4 in [../README.md](../README.md) says to copy vanilla's
rules rather than invent better ones; this was an invention.

The one operation that is genuinely awkward in vanilla is `min` of two varying signals — see
[analog-and-gate.md](analog-and-gate.md). If any of this comes back, that is the piece worth
bringing, and it should come back as what it really is — a comparator operation — rather than as a
mode on all three gates.

## What the three shared, if they ever return

* A `analog` boolean block state, and the click cycle walking `inverted` × `analog`.
* An amethyst stripe, 2 × 7 pixels down the plate's left flank, as the mode's marker: amethyst is
  the only saturated purple in vanilla's palette and cannot be mistaken for redstone.
* Twelve more textures and twelve more models, all generated, and 256 block state variants per gate
  instead of 128.
* Negation defined as the complement, `15 − x`: it agrees with boolean NOT at both ends, it is its
  own inverse, and it reverses order, so De Morgan's laws hold exactly — `15 − min(a,b)` really is
  `max(15−a, 15−b)`.
