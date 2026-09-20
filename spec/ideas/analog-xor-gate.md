# Idea: analog XOR gate — `abs(a − b)`

**Status:** built, then withdrawn on 2026-09-20. Not committed to.
**Would belong to:** [../blocks/xor-gate.md](../blocks/xor-gate.md), as a second evaluation mode.
**Shared context:** [README.md](README.md).

## What it does

XOR means "the inputs disagree", and the analog measure of *how much* they disagree is the distance
between them. Inverted, `15 − abs(a − b)` becomes an agreement meter: 15 when the inputs are
identical, falling as they diverge.

| a | b | XOR | XNOR |
| --- | --- | --- | --- |
| 7 | 7 | 0 | 15 |
| 7 | 8 | 1 | 14 |
| 4 | 9 | 5 | 10 |
| 0 | 15 | 15 | 0 |

## What it is worth

The most useful of the three, and the one that compresses the most vanilla: `abs(a − b)` is
`max(a − b, b − a)`, which is two subtract comparators pointing opposite ways with their outputs
merged on dust — one of the two is always 0, so the junction picks the other.

* **Imbalance meter.** Two silos feeding one machine: the output *is* the balance error, and past a
  threshold you switch the input to the emptier one.
* **Error against a set point.** One input held at a reference, the other measured: the output is
  the deviation.
* **Equality detector**, inverted. 15 only when the two inputs match exactly. Item frames give 1–8
  by rotation, so a row of XNOR gates feeding an AND is a combination lock with one dial per frame.
* **Analog change detector.** Feed a signal and a delayed copy of itself: the output is how much the
  level just moved.

**But** — and this is why it is only *partly* worth it — a comparator in subtract mode already gives
`max(0, a − b)`. Whenever it is known which input is the larger, that single block does the same job
and the gate adds nothing. The gate earns its place only when the sign is unknown or changes, which
is exactly the silo case.

## The wrinkle that never got resolved

In digital logic XOR is `(a ∧ ¬b) ∨ (¬a ∧ b)`. **In analog, with these same functions, that identity
breaks:**

| a | b | `abs(a − b)` | `max(min(a, 15−b), min(15−a, b))` |
| --- | --- | --- | --- |
| 4 | 9 | 5 | 9 |
| 7 | 8 | 1 | 8 |

So the analog XOR could not be built out of the mod's own analog AND, OR and NOT, although the
digital one can be built out of their digital forms. `abs(a − b)` was chosen anyway, because the
decomposable version answers 8 for two inputs that differ by one, which is useless as a difference
meter — fuzzy logic settles on the distance for the same reason. Had the mode shipped, this
exception needed writing down in the gate's own spec, and it never was.

## Cost of bringing it back

Everything in [README.md](README.md) under *What the three shared*, plus `applyAnalog` on
`GateOperation` returning `Math.abs(a - b)`.
