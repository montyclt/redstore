# Idea: analog OR gate — `max(a, b)`

**Status:** built, then withdrawn on 2026-09-20. Not committed to.
**Would belong to:** [../blocks/or-gate.md](../blocks/or-gate.md), as a second evaluation mode.
**Shared context:** [README.md](README.md).

## What it does

Emits the stronger of the two inputs, keeping its level. Inverted, it emits `15 − max(a, b)`.

| a | b | OR | NOR |
| --- | --- | --- | --- |
| 0 | 0 | 0 | 15 |
| 4 | 9 | 9 | 6 |
| 7 | 15 | 15 | 0 |
| 15 | 15 | 15 | 0 |

## What it is worth

**Very little, and this is the weakest of the three.** Two dust lines meeting already take the
maximum of what feeds them — `max` is free in vanilla and costs no block at all. What the gate would
add is not the function but two side effects of it:

* **Isolation.** The two sources cannot feed back into each other, which a bare dust junction does
  not guarantee.
* **One cell.** A junction needs room; the gate is the plate that is already there.

The inverted form is the more interesting half. If the inputs are container fill levels,
`15 − max(a, b)` is the **free space of the fuller container**, which is the capacity a storage
system can actually promise. In vanilla that is the dust junction plus one subtract comparator
against a block of redstone.

There is also a degenerate case worth knowing: with one input at 0, `max(a, 0) = a`, an analog
repeater that does not reset the level to 15. Vanilla already has that too — a comparator in compare
mode with nothing on its sides passes its back input through unchanged.

## Cost of bringing it back

Everything in [README.md](README.md) under *What the three shared*, plus `applyAnalog` on
`GateOperation` returning `Math.max(a, b)`.
