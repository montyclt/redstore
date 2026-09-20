# Idea: analog AND gate — `min(a, b)`

**Status:** built, then withdrawn on 2026-09-20. Not committed to.
**Would belong to:** [../blocks/and-gate.md](../blocks/and-gate.md), as a second evaluation mode.
**Shared context:** [README.md](README.md).

## What it does

Instead of treating the inputs as booleans, the gate keeps their strengths and emits the smaller of
the two. Inverted, it emits `15 − min(a, b)`.

| a | b | AND | NAND |
| --- | --- | --- | --- |
| 0 | 0 | 0 | 15 |
| 4 | 9 | 4 | 11 |
| 7 | 15 | 7 | 8 |
| 15 | 15 | 15 | 0 |

With the inputs restricted to 0 and 15 the table collapses onto the digital one, so switching mode
on a saturated build changes nothing. That property is what made the mode look safe.

## What it is worth

**This is the one analog operation that vanilla makes genuinely awkward.** `min(a, b)` is
`a − max(0, a − b)`, which is two comparators in series *and* the signal `a` delivered to both of
them at the same strength. That means splitting a dust line into two branches of exactly equal
length; get the lengths wrong and the result is quietly incorrect. It is precisely the kind of
fragile hand-built arithmetic this mod exists to remove.

Uses:

* **Guaranteed common stock.** Two containers holding the two ingredients of a recipe: `min` is how
  many times the recipe can actually run, because the scarcer one is the limit. One block turns two
  comparator readings into the number that matters.
* **Limiter.** Hold one input at a constant and `min` clamps everything above it. Against a fixed 8,
  any reading over 8 comes out as 8.

## What it is not worth

An **analog valve** — pass a level through when enabled, cut it when not — looked like a use for
this and is not: a comparator in subtract mode with 15 on its side outputs 0, and with 0 on its side
passes the input unchanged. One block, no gate needed. This was claimed as a use case during design
and is wrong.

## Cost of bringing it back

Everything in [README.md](README.md) under *What the three shared*, plus `applyAnalog` on
`GateOperation` returning `Math.min(a, b)`.

If only this one operation comes back, it should not be a mode on the AND gate at all — it is a
comparator operation, and it would sit better as its own block or as a comparator-shaped component.
