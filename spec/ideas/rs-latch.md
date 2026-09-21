# Idea: RS latch plate

**Status:** proposed on 2026-09-21. Not committed to.
**Would belong to:** [../blocks/abstract-redstone-plate.md](../blocks/abstract-redstone-plate.md),
as a new plate in the family — and geometrically it is a logic gate.
**Shared context:** [README.md](README.md).

## What it does

Two inputs on the side faces and one output at the front, exactly the geometry of a gate
([../blocks/abstract-logic-gate.md](../blocks/abstract-logic-gate.md) §1): **set** on one flank,
**reset** on the other. A pulse on set turns the output on, a pulse on reset turns it off, and
between pulses the output holds.

| Set | Reset | Output |
| --- | --- | --- |
| off | off | holds |
| on | off | on |
| off | on | off |
| on | on | **see below** |

## Its vanilla equivalent, and what it costs there

The textbook **RS NOR latch**: two redstone torches on two blocks, each torch's output feeding the
other's block. Two cells plus the wiring that reaches them, and it is the one contraption in this
directory that is genuinely small in vanilla already.

Which is the argument against the block: what it would replace is two cells, not twenty, so there
is not much to compress.

**The mod's own gates do not help here, and that cuts the other way.** A NOR gate is an inverted OR
gate and an RS latch is two cross-coupled NOR gates, so it is tempting to say the mod already
builds one. It does not, in any useful sense. A gate reads its flanks and emits from its front, so
cross-coupling two of them means routing each output back round to the other's side — dust the two
torches never needed, since a torch's block *is* the coupling. Built out of Redstore gates a latch
comes out **larger** than the vanilla contraption it imitates, which is the one place in the mod
where using it makes a circuit worse.

So the honest accounting is not "the gates already do it". It is that a dedicated block would beat
both — the gates by a lot, vanilla's torches by about a cell.

## What it is worth

* **Holding a state a circuit sets and another clears.** Two sensors, one memory: a hopper minecart
  arriving sets it, the unloading finishing clears it.
* **An edge turned into a level**, which is what most contraptions actually want from a button.
* It is the primitive the [T flip-flop](t-flip-flop.md) is built from, which is why the two ideas
  arrived together — but a T flip-flop is *not* two latches away, so having one does not give the
  other.

## The question that has to be answered first

**What happens when set and reset are both on?** Vanilla answers this, and the answer should be
copied rather than invented (design rule 4). In a NOR latch, `Q = NOR(R, Q̄)`: while R is high, Q is
0 no matter what the other side is doing. So the faithful behaviour is **reset wins** — the output
goes off and stays off while both are held, and the state when they are released together is
whichever input is released last.

That is worth writing down here because the tempting alternatives — "set wins", "hold the previous
value" — are both rules vanilla does not have, and both would make this block a thing a player has
to learn separately from the torches it replaces.

## Open questions

* **Does right-click invert it?** The gates use their click for inversion and this block has the
  gates' shape, so the obvious setting is `Q` versus `Q̄` on the front face. An RS latch in vanilla
  naturally has both outputs; ours has one face, so the click would choose which one it shows.
* **Delay.** The gates take two redstone ticks. A latch built from two of them takes two more.
  A single block could be faster than the thing it replaces, which is the usual trap.
* **What does it show?** Every other plate says what it is on its face. A latch's face would have to
  say which of its two flanks is set and which is reset, or a builder cannot wire it without
  guessing — and that is the first plate in the family whose two sides are *not* interchangeable.

## Why it is not in the mod today

Because the contraption it compresses is already small. A cell saved over two torches is a thin
return for the family's first plate whose two sides are not interchangeable, and for the face that
would have to tell a builder which flank is which.

That is a judgement about the size of the prize, not about the block being wrong, and it is worth
revisiting the moment someone builds a machine that wants a dozen latches. If it is ever built it
should be weighed together with the [T flip-flop](t-flip-flop.md): the two are the same question —
whether the mod remembers — and that is a decision to take once, deliberately, rather than by
adding one memory block at a time.
