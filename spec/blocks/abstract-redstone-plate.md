# Redstone plate — abstract spec

> **This is an abstract spec.** It describes no block: it has no identifier, no recipe, no item and
> never appears in game. It defines everything the flat Redstore redstone components share, so that
> each concrete block's spec only has to state what that block adds or overrides.

## 1. Hierarchy

```
Redstone plate (abstract)          abstract-redstone-plate.md   ← this file
├── Logic gate (abstract)          abstract-logic-gate.md
│   ├── AND gate                   and-gate.md          redstore:and_gate
│   ├── OR gate                    or-gate.md           redstore:or_gate
│   └── XOR gate                   xor-gate.md          redstore:xor_gate
└── Redstone clock                 redstone-clock.md    redstore:redstone_clock
```

Every statement in this file holds for every leaf of that tree unless the leaf's own spec says it
overrides it. A subclass may **add** state, faces and interactions; it may not silently contradict
this file.

**The Java hierarchy deliberately differs from this one.** Inheritance in code is an is-a
relationship, and a logic gate *is* a diode: a flat plate that reads redstone, waits a tick and
emits from its front. So `LogicGateBlock` extends vanilla's `DiodeBlock` and inherits the shape,
the placement, the output, the delay, the tick scheduling and its priority, and the type check that
decides what can lock a repeater. A clock is **not** a diode — nothing feeds it and it answers to
no input — so it extends `RedstonePlateBlock`, which is this file made code.

The family in this document is about how the blocks look and behave. The family in Java is about
what they are. They are allowed to disagree, and here they do.

## 2. Physical form and placement

* Shape: `Block.box(0, 0, 0, 16, 2, 16)` — the same footprint and height as a repeater.
* Placed on the **top face of a block only**: `canSurvive` requires
  `canSupportRigidBlock(level, pos.below())`. It pops off as an item when the support goes.
* Not waterloggable, consistent with repeaters and comparators.
* Block properties:

```java
BlockBehaviour.Properties.of()
    .instabreak()
    .sound(SoundType.STONE)
    .pushReaction(PushReaction.DESTROY)
    .lightLevel(state -> outputIsHigh(state) ? 7 : 0)
```

  `outputIsHigh` is whichever state property the subclass uses for its output (§8).

## 3. Orientation

`FACING` is `HorizontalDirectionalBlock.FACING` and points at the **output**, the same convention
vanilla uses for repeaters and comparators. `getStateForPlacement` returns
`context.getHorizontalDirection().getOpposite()`, so the output points away from the player who
places it.

Face names used throughout the specs, for a plate facing north:

| Name | Direction | |
| --- | --- | --- |
| front | `facing` | always the output |
| left | `facing.getCounterClockWise()` | west |
| right | `facing.getClockWise()` | east |
| back | `facing.getOpposite()` | south |

Which of left / right / back a subclass actually reads is the subclass's business.

## 4. Reading a signal from a face

Every input face of every plate is read with **exactly the rule vanilla uses for a comparator's
side inputs**, regardless of which face it is:

```java
int v = level.getControlInputSignal(pos.relative(dir), dir, false); // diodesOnly = false
```

| Source in the adjacent cell | Feeds the input? |
| --- | --- |
| Redstone dust | yes, at its power level |
| Block of redstone | yes, 15 |
| Repeater or comparator pointing at the plate | yes |
| Observer pointing at the plate | yes |
| Anything else emitting a *direct* signal towards the plate | yes |
| A solid block that is itself powered | **no** |
| A redstone torch, lever or button standing in that cell | **no** — they send their direct signal into the block they are attached to, not sideways |

In one sentence: **an input face of a plate behaves exactly like the side of a comparator.**

### 4.2 An input is not a lock

The rule above governs faces that carry a **value** into the block. A face that **locks** the
block instead follows vanilla's other rule, the one that locks a repeater, because it is the same
mechanism and should behave the same way.

That rule is not about signal strength at all: it is a type check. Vanilla asks
`DiodeBlock.isDiode(state)` — literally `getBlock() instanceof DiodeBlock` — and only then reads
that block's *direct* signal towards the face. A block of redstone against the side does nothing,
however strong it is, because it is not a diode. Dust does nothing. A lever does nothing. Only a
repeater or a comparator aimed at the face, and by extension anything that counts as a diode.

Redstore needs no widening of that set, because **its gates are real diodes**: they extend
`DiodeBlock`, so vanilla's own type check accepts them. A gate can lock a repeater, a comparator's
side can read it, and a gate can stop a clock — all without a special case anywhere in this mod.
That is the payoff of getting the inheritance right rather than imitating.

A clock cannot lock anything, since it is not a diode. Stopping one clock with another takes a
repeater in between, which is the same answer vanilla gives for anything that is not a diode.

### 4.1 Why the restrictive rule

The permissive alternative is the rule a repeater uses on its **back** face, which additionally
reads *through* a solid conductor (`getSignal` falls back to `getDirectSignalTo` when the neighbour
is a redstone conductor). It is rejected for two reasons:

* **The restrictive rule exists for exactly this geometry.** The faces of a plate are unavoidable
  contact surfaces in a compact circuit: they touch structural blocks that are powered for
  unrelated reasons. With the permissive rule, dust running over the block next to a plate would
  hold that input high permanently, and two parallel circuit lines separated by one block would
  cross-talk. A vanilla diode can afford the permissive rule on its back because it has exactly one
  back face and the builder chooses what goes there; a Redstore gate has **two** inputs, so the
  problem would be doubled, not halved.
* **No new rules to learn.** Anyone who already uses comparators knows this behaviour and can
  predict the block without reading documentation. A third rule invented by the mod — "read the
  neighbouring component, but never through a block" — would be marginally more convenient (a lever
  next to the plate would work); it was considered and dropped, because convenience does not
  justify a rule that exists nowhere in vanilla. See also design rule 4 in
  [../README.md](../README.md).

The accepted cost is that a torch or lever placed directly beside an input does nothing; one
redstone dust between them fixes it, which is exactly what a comparator already forces builders
to do.

## 5. Output

* `isSignalSource(state)` → `true`.
* `getSignal` and `getDirectSignal` both return the current output when
  `direction == state.getValue(FACING)`, and 0 for every other direction.
* The block in front is therefore **strongly powered**, exactly like a repeater's target, so dust
  on top of it lights up.
* No other face ever emits a signal.

### 5.1 Known cosmetic wart

Redstone dust connects visually to any block whose `isSignalSource()` is true, so dust placed
against a plate's unused faces (a gate's back, a clock's sides) will draw itself as connected while
never carrying a signal. Suppressing it needs a mixin into `RedStoneWireBlock#shouldConnectTo`,
which is listed as optional polish in [../roadmap.md](../roadmap.md).

## 6. Timing principle

A plate never produces a timing that a vanilla contraption cannot. Concretely: no 0-tick outputs,
no 1-game-tick pulses, and every state change goes through the vanilla tick scheduler
(`level.scheduleTick(pos, this, delayInGameTicks)`), guarded against double scheduling with
`level.getBlockTicks().hasScheduledTick(pos, this)`. Each subclass states its own delay and must
state its vanilla equivalent.

## 7. Interaction grammar

**One interaction per plate: a plain right-click, which advances the block to its next setting and
wraps around.** A plate with more than one adjustable axis walks all of them in one cycle — the
clock's four delays in each of its two modes, the gates' inversion in each of their two evaluation
modes.

* No plate binds anything to sneak + right-click. Vanilla only delivers a sneaking click to a block
  when the player's hands are empty — it places the held item instead — so a setting reachable only
  that way is unreachable exactly when a builder needs it, mid-build, with a stack in hand. This
  was tried and removed.
* The click is handled so that holding a block never places it against the plate, matching vanilla
  repeater behaviour.
* Each click plays `SoundEvents.COMPARATOR_CLICK`; the subclass picks the pitch, and pitch should
  encode the setting so the cycle is audible without looking.
* The click returns `InteractionResult.SUCCESS` and immediately re-evaluates the block.

## 8. Block state

The base contributes exactly one property:

| Property | Type | Values |
| --- | --- | --- |
| `facing` | `HorizontalDirectionalBlock.FACING` | `north/south/east/west` |

Each subclass adds its own, including the property that represents its output — a boolean for a
block whose output is only on/off, an `IntegerProperty` 0–15 for one that can emit intermediate
strengths.

## 9. Model template

Every plate is the **vanilla repeater's own model with its textures swapped**, not a model built
from scratch. That is what makes the family read as part of the redstone family at a glance, and
it is free: vanilla already solved the plate's geometry, its edge shading and the sliding torch.

* Base slab `0,0,0 → 16,2,16`. Its `slab` texture stays `minecraft:block/smooth_stone`; only the
  `top` face is a mod texture.
* The **top texture is derived from `block/repeater`**, not from smooth stone. That texture is the
  stone plate *plus* the unlit redstone line painted along the torch track at columns 7–8, rows
  6–13. Lit variants derive from `block/repeater_on`, which gives vanilla's exact lit red for free.
* Torch elements come from the vanilla model too, including the glow quads that the lit models
  carry. A plate keeps whichever torches it needs and drops the rest.
* The block state file maps `facing` to the `y` rotation using the repeater's own convention —
  south 0, west 90, north 180, east 270 — so a plate's torch travel reads the same way round as a
  repeater's.
* The item model is the 3D block model of the block's default state.

### 9.1 Glyphs

What distinguishes one plate from another is engraved on the top texture, in vanilla's own
redstone red (`#580101` unlit, `#D70304` lit), on the strip the torches do not cover.

Glyphs are authored as **ASCII art inside the `generateAssets` Gradle task**, one character per pixel,
and stamped onto the derived base. **A glyph must be an even number of pixels
wide**, or it cannot be centred on a 16-pixel face and will sit a pixel off. That constrains the
art: a repeated motif of `n` elements of width `w` with gaps `g` measures `n·w + (n−1)·g`, so an
odd count of odd-width elements is ruled out before it is drawn. An off-centre glyph is not an
acceptable result; the motif changes instead. See [redstone-clock.md](redstone-clock.md) §6.1 for
the worked case. There is no hand-drawn PNG anywhere in the family: a new plate
costs a handful of lines in that file, and the diff shows the actual pixels changing. See
[../conventions.md](../conventions.md) §10.

## 10. Recipes are parts lists

**Every ingredient must be visible on the block, and every visible element must be in the recipe.**
This is vanilla's own rule, followed exactly: a repeater costs two redstone torches, one dust and
three stone, and shows two torches and a painted dust line on a stone plate; a comparator costs
three torches, one quartz and three stone, and shows three torches and a quartz inlay. Not one
ingredient is invisible, and nothing on the block is unaccounted for.

So a plate's recipe is written by reading the block:

* **The bottom row is always three smooth stone** — that is the plate itself.
* The rest of the grid lists what sits on top: a torch for each torch, redstone dust for anything
  painted red, and whatever the block's distinguishing piece is made of.

A plate whose recipe does not survive that reading is wrong, and it is the recipe or the texture
that changes, not the rule. An earlier draft had all four plates share one decorative frame with
quartz and dust in it, neither of which appeared anywhere on any of the blocks; that is how this
rule got written down.

## 11. Loot and tags

* Loot table: drops itself, single roll, `ExplosionCondition.survivesExplosion`.
* Tags: `redstore:redstone_plates` and `minecraft:mineable/pickaxe` for every plate. No tool is
  required for drops (`instabreak`).
* **A recipe-unlock advancement**, which is not optional: a recipe only appears in the crafting
  table's book once something unlocks it, and without one the block is craftable but invisible to
  anyone who does not already know the shape. It lives in
  `data/redstore/advancement/recipes/redstone/<name>.json` and follows vanilla's own pattern —
  `inventory_changed` on the ingredient that says what the block is for, plus `recipe_unlocked`,
  granting the recipe.
* No achievement-style advancements.

## 12. Java mapping

```java
public abstract class RedstonePlateBlock extends HorizontalDirectionalBlock {
    protected int readFace(SignalGetter level, BlockPos pos, Direction dir);   // comparator rule
    protected int readLock(SignalGetter level, BlockPos pos, Direction dir);   // repeater's lock rule
    protected abstract int outputSignal(BlockState state);
    protected abstract InteractionResult onClick(BlockState state, Level level, BlockPos pos, Player player);
}
```

This class exists for the plates that are **not** diodes — today, the clock. It carries the shape,
the placement rules, `FACING`, the front-only strong output, the two reading rules and the click
dispatch.

A plate that *is* a diode does not use it. `LogicGateBlock extends DiodeBlock` and gets all of the
above from vanilla, overriding only two hooks — `getDelay` and `shouldTurnOn` — plus
`sideInputDiodesOnly` to say that its side faces are inputs rather than a lock. The duplication
between the two base classes is the price of Java having one superclass per class, and it is small:
vanilla's diode already provides what this file describes.

## 13. What every subclass must define

A concrete plate spec is complete when it answers all of these:

| # | Member |
| --- | --- |
| 1 | Identifier and display names (en_us, es_es) |
| 2 | Which faces are inputs, and what each one means |
| 3 | The output function, for every mode the block has |
| 4 | Its delay, and the vanilla contraption it is equivalent to |
| 5 | The state properties it adds, including the output property |
| 6 | The full cycle the click walks, in order, with sound pitches |
| 7 | Its top textures, its torch layout and its model count |
| 8 | Its recipe, read as a parts list of what the block shows |
| 9 | Any tag beyond `redstore:redstone_plates` |

## 14. What 26.3 provides, shared

Confirmed against the decompiled jar: `HorizontalDirectionalBlock.FACING`, `Block.box`,
`Block.canSupportRigidBlock`, `SignalGetter#getControlInputSignal(BlockPos, Direction, boolean)`,
`Level#scheduleTick(BlockPos, Block, int)`, `Level#getBlockTicks().hasScheduledTick`,
`useWithoutItem`, `tick(BlockState, ServerLevel, BlockPos, RandomSource)`,
`neighborChanged(..., Orientation, boolean)`,
`updateShape(BlockState, LevelReader, ScheduledTickAccess, …)`, `PushReaction.POPPED` (not
`DESTROY`), and that a block in 26.3 carries no `MapCodec`.

Still open:

- [ ] `SignalGetter#getControlInputSignal(BlockPos, Direction, boolean)` name and signature.
- [ ] That `diodesOnly = false` is still what `ComparatorBlock` uses for its sides
      (`DiodeBlock#sideInputDiodesOnly` → `false` for the comparator, `true` for the repeater's
      locking check).
- [ ] That the vanilla `DiodeBlock` convention still has `FACING` pointing at the output.
- [ ] `HorizontalDirectionalBlock` / `BlockBehaviour` shape-update method names, reworked across
      the 1.21.x line.
- [ ] `BlockBehaviour.Properties#lightLevel` vs `#lightEmission` naming.
- [ ] `Level#scheduleTick` / `LevelTickAccess#hasScheduledTick` signatures.
