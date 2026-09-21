# Idea: vertical wire

**Status:** proposed on 2026-09-21. Not committed to.
**Would belong to:** nowhere yet — it is not a plate and not a diode, so it would be the first
member of a second family.
**Shared context:** [README.md](README.md).

## What it does

A block that carries a redstone signal **up and down a column**, so a circuit can change level
without a torch ladder or a staircase of dust. One block per level, stacked; dust at the bottom and
at the top of the stack reads and feeds it the way dust meets dust.

## Its vanilla equivalents, and what they cost there

Vertical is the one direction vanilla makes genuinely awkward, and it offers three different
answers, each paying a different price:

| Going | Technique | What it costs |
| --- | --- | --- |
| up | **Torch ladder** — solid block, torch on its side, repeat | one redstone tick per level, **inverts at every step**, and a 1 × 2 footprint per level |
| down | **Staircase of dust** on slabs or stairs | one level of **signal strength per block**, so 15 blocks maximum, plus a block of horizontal room per step |
| either, pulses only | **A column of observers** | two game ticks per block, and it carries an **edge**, never a level |

So vanilla has no lossless, instant vertical wire. Every route either costs time, costs signal
strength, or only carries a pulse.

## What it is worth

Height is where technical builds get ugly. A sorter under a farm, a machine that reports to a
control room upstairs, anything at all in a multi-storey base: the vertical run is usually bigger
than the circuit it connects, and it is always the part that gets rebuilt when a floor moves.

This is also the idea a player would reach for most often — which is precisely why it is the
dangerous one.

## The question that decides whether it can exist

**What does it pay?** Design rule 1 says the block may only do what a player can already do in
survival, and rule 4 says to borrow vanilla's rules rather than invent better ones. A column that
carries a signal instantly, losslessly, and however high, is a **better wire than vanilla has**,
and the mod's own non-goals already rule out "anything that has no vanilla equivalent".

So it has to pay one of vanilla's prices, and the choice is the whole design:

* **A level of signal per block, like dust.** Fifteen blocks of reach, a repeater needed past that,
  and the rule is one every player already knows without being told. This is the honest option.
* **A redstone tick per block, like a torch ladder.** Unlimited height, no strength loss, and a
  delay that grows with the climb — which is the price the ladder charges today, minus the
  inversion.
* **Both**, which is strictly worse than either technique it replaces and would simply not be used.

The first is the one to build if this is ever built. The second is defensible and turns the block
into something closer to a vertical repeater.

## Open questions

* **Does it invert?** A torch ladder does, once per level; two levels cancel. Ours should not — an
  inverting wire whose sign depends on how far it travelled is exactly the kind of rule a builder
  should not have to count.
* **One direction or both?** Simplest is neither: the column behaves like a piece of dust standing
  on end, taking the strongest signal that reaches either end and presenting it at the other.
* **What connects to it?** If dust connects on every face it becomes a general-purpose bus and the
  circuits around it get harder to read. It may need to accept and emit only at the top and bottom
  of a stack.
* **Cost when placed in the hundreds.** This is wire, not a component: a base would hold far more of
  these than of every other Redstore block put together, and each one that updates its neighbours
  is a block update. The chunk loader's rule — place more of them, the cost stays visible and
  linear — matters here too.
* **What is it made of, and what does it look like?** A pillar of some kind. It is the first block
  in the mod that is not a flat plate or a vanilla-shaped container, so it has no art to derive
  from, the same problem [../blocks/chunk-loader.md](../blocks/chunk-loader.md) has.

## Why it is not in the mod today

It is the furthest from the charter of everything considered so far. The other blocks compress a
contraption into a cell and keep its rules; this one compresses a *technique* and then has to
invent a price for itself, because vanilla's three answers charge three different prices and none
of them is obviously the one to copy. Until that price is chosen and written down, the block cannot
be specified — and choosing it is the whole design, not a detail.
