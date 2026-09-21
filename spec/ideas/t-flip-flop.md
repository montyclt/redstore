# Idea: T flip-flop plate

**Status:** proposed on 2026-09-21. Not committed to.
**Would belong to:** [../blocks/abstract-redstone-plate.md](../blocks/abstract-redstone-plate.md),
as a new plate in the family.
**Shared context:** [README.md](README.md).

## What it does

One input at the back, one output at the front. Every **rising edge** on the input flips the output,
which then holds until the next edge. A button in front of it behaves like a lever; a clock in front
of it becomes a clock of half the frequency.

| Input | Output |
| --- | --- |
| off → on | flips |
| on → off | nothing |
| held on | nothing |

## Its vanilla equivalent, and what it costs there

This is one of the oldest survival contraptions and it has several forms:

* **Piston T flip-flop.** A sticky piston pushing a block of redstone between two positions, with
  the piston's own arm feeding back. Roughly 3 × 2 × 2, noisy, and it moves a block every pulse.
* **Dropper T flip-flop.** Two droppers facing each other with a single item between them and a
  comparator reading one of them. Quieter, entirely block-entity driven, about the same volume.
* **Observer variants**, which do the edge detection in one block and still need the memory.

None of them is difficult. All of them are bulky, and all of them are the kind of thing a builder
copies from memory and then spends ten minutes debugging because it is off by one tick. That is
exactly the tedium this mod exists to compress, so of the three ideas in this directory this is the
one that best fits the charter.

## What it is worth

* **A button that latches.** The commonest use by far: doors, lights and machines driven from a
  button instead of a lever, so a player never leaves one switched on.
* **Frequency division.** Feeding it a clock halves the rate, and chaining them gives 1/2, 1/4,
  1/8 — a binary counter, one block per bit.
* **Alternation.** Two outputs that must never be on together fall out of the block for free: the
  output and its inverse.

## Open questions

**Edge detection has to be genuine.** A level-triggered version oscillates as long as the input is
held, which is a clock, not a flip-flop. The block must remember the input's previous level, which
is a second bit of state beyond the output.

**What is its delay?** The plate family says one redstone tick, and the gates say two. A flip-flop
must also decide what happens to an input pulse *shorter* than that delay: a gate stretches it
(see [../blocks/abstract-logic-gate.md](../blocks/abstract-logic-gate.md) §3), but a flip-flop that
swallows a 1-tick pulse is useless next to a clock in pulse mode, which this mod also sells.

**Should a click flip it by hand?** The family's interaction grammar is one click, one setting. Here
the obvious candidate for the click is the state itself, which would make the block a lever a
circuit can also throw. Tempting, and it breaks the grammar: every other plate's click chooses a
*mode* and never touches its output.

**Is it still vanilla-equivalent when it is this easy?** Nothing here does anything the piston
version cannot. But a T flip-flop as one instant, silent, tileable cell is enough of a leap in
convenience that circuits which are painful today become routine — which is the point, and also the
thing to be honest about.

## Why it is not in the mod today

Only because nothing has needed it yet. Unlike the analog gates, it duplicates no vanilla block: the
comparator does arithmetic, the repeater delays, and **nothing in vanilla remembers one bit in one
cell** except a lever, which a circuit cannot throw. If the mod grows past its five blocks, this is
the first candidate.
