# Redstone clock

`redstore:redstone_clock`

A plate that emits a periodic signal with a settable period of 1 to 4 redstone ticks per phase. It
replaces a vanilla repeater loop plus its lever, in one block and one cell.

**Extends:** [abstract-redstone-plate.md](abstract-redstone-plate.md). Everything not stated in
this file is inherited: the repeater-sized plate and its placement rules, `facing` pointing at the
output, the comparator-side reading rule for input faces, the front-only strong output, the
interaction grammar, the model template, the recipe principle and the loot table.

It is **not** a logic gate and is deliberately outside `redstore:logic_gates`.

## 1. Defined members

| Member | Value |
| --- | --- |
| Identifier | `redstore:redstone_clock` |
| Display name (en_us) | Redstone Clock |
| Display name (es_es) | Reloj de redstone |
| Input faces | the two sides, as a **stop** input, read with the repeater's lock rule (§2) |
| Output | 15 or 0; no analog mode |
| Main property (right-click) | `delay`, cycling 1 → 2 → 3 → 4 |
| Second axis of the same cycle | `pulse`, square wave ↔ 1-tick pulse |
| Recipe | two torches, one clock, one dust, three smooth stone |
| Extra tags | none beyond `redstore:redstone_plates` |

## 2. Running and stopping

**The clock runs by default and a redstone signal on either side stops it.** This is the one place
in the mod where the usual "signal = go" convention is inverted, and it is deliberate: a clock that
needed a permanent input would spend a cell on a lever and a dust for the common case, which is a
clock that simply runs.

```
                north  =  OUTPUT (front)
                  ^
                  |
   west  X ---> [ ⎍ 1-4 ] <--- X  east      either side stops it
                  |
                south  =  back, unused
```

* **The stop input is the two side faces**, read with the inherited rule
  ([plate §4](abstract-redstone-plate.md)). Any value greater than 0 on either one stops the clock.
* Stopping is immediate: the output drops to 0 and the phase resets, so releasing the stop signal
  starts a fresh ON phase, with the one caveat in §2.3. No half-finished pulses.
* A stopped clock shows the **bedrock bar** across the plate, in the sliding torch's place, exactly
  like a locked repeater. The bar sits at the torch's position, so the setting stays readable while
  the clock is stopped. The block state property is `locked`, the same name the repeater uses.

### 2.1 Why the sides, and why the lock rule

Two faces are unusable for this. The **front** is the output, and reading it would mean the clock
switches itself off the moment anything is wired to it — which is exactly what an earlier version
of this block did. The **back** is `FACING` itself, which in vanilla's diode convention is a
diode's *input* side; a clock has no input, but reserving that face keeps the plate family's
geometry consistent and leaves room for one later.

That leaves the sides, which is also where a repeater takes the signal that locks it. Since the
mechanism is borrowed, **the rule is borrowed with it**: the lock responds to a diode aimed at the
face and to nothing else, and the block shows the bedrock bar while it is held. Costing an extra
repeater to stop a clock from a lever is the price of behaving like the thing it imitates, and a
builder who has ever locked a repeater already knows how it works without being told.

An earlier draft used the ordinary input rule here, so dust into the side stopped the clock. It was
cheaper to wire and it was wrong: two mechanisms that look identical on the block behaved
differently.

### 2.2 `FACING` points at the back, not the output

Worth writing down because it cost a bug: in 26.3 a diode's `FACING` is the direction of its
**input**, and its output comes out of `FACING.getOpposite()`. `DiodeBlock#getInputSignal` reads
`pos.relative(FACING)`, and `getSignal` returns the output when the querying direction equals
`FACING`, because that parameter points from the powered block back towards the diode. The plate
base class inherits both conventions verbatim, so anything written against it must too.

### 2.3 A restart can ride the pending tick

Stopping does not cancel the scheduled tick; it lets it fire, see the block locked and decline to
reschedule. Starting, in turn, schedules nothing if a tick is already pending. So a clock that is
released *before* that pending tick fires resumes on it rather than on a fresh one, and its first
ON phase is only as long as whatever was left of it — up to one phase short.

This is the cheap reading of "the phase resets" above, and it is deliberate: the alternative is
storing the phase in the block state and paying for it in block states and in code. It is written
down rather than hidden because a builder timing a machine off the first pulse after a lever will
meet it. Whether it is worth fixing is open.

## 3. Period and modes

The setting **N ∈ {1, 2, 3, 4}** always means the same thing: **the period is 2N redstone ticks**
(4N game ticks). The mode chooses only the duty cycle.

| Mode | Output | Period |
| --- | --- | --- |
| **Square** (`pulse = false`, default) | ON for N redstone ticks, OFF for N redstone ticks | 2N |
| **Pulse** (`pulse = true`) | ON for 1 redstone tick, OFF for 2N − 1 redstone ticks | 2N |

```
N=2, square:  ██__██__██__      period 4 rt
N=2, pulse:   █___█___█___      period 4 rt
N=4, square:  ████____████____  period 8 rt
N=4, pulse:   █_______█_______  period 8 rt
```

Consequences, all intentional:

* Switching mode never changes the frequency, only the shape, so a build's duty cycle can be
  retuned without re-timing anything downstream.
* At N = 1 both modes are identical (ON 1, OFF 1). That is not a special case in the code, it falls
  out of the formula.
* The fastest clock is 2 redstone ticks per cycle. There is deliberately no 0-tick or
  1-game-tick clock.

### 3.1 Scheduling

* The block reschedules itself: on each scheduled tick it flips the output, writes the new state
  with `Block.UPDATE_ALL`, and schedules the next flip at the length of the phase it just entered
  — `2 × N` game ticks in square mode; `2` game ticks for the ON phase and `4N − 2` for the OFF
  phase in pulse mode.
* Double scheduling is guarded with the inherited `hasScheduledTick` check.
* **The next tick is scheduled before the state is written, never after.** `setBlock` notifies the
  neighbours synchronously; a powered dust in front notifies the clock straight back, and anything
  that inspects the tick queue in that window would see no pending tick and start a second clock on
  top of the first. Writing the state first is what made an early version freeze on as soon as its
  output was wired to anything.
* For the same reason, `neighborChanged` reacts **only** to a change in `locked`. A neighbour has
  nothing else to say to this block, and reacting to more means reacting to the block's own
  output.
* While stopped, nothing is scheduled. When the stop signal is released, the clock immediately
  enters an ON phase and schedules the next flip.
* Changing `delay` or `pulse` does **not** reset the phase: the change applies from the next phase
  boundary, so a running clock can be retuned without glitching.

## 4. Block state

Adds to the base's `facing`:

| Property | Type | Values | Meaning |
| --- | --- | --- | --- |
| `delay` | `IntegerProperty` 1–4 (`BlockStateProperties.DELAY`) | `1` default | N, the setting. Named `delay` to match the repeater. |
| `pulse` | `BooleanProperty` | `false` default | Pulse mode instead of square. |
| `powered` | `BooleanProperty` | `false` default | Current output state; also the lit flag. |

64 states. No block entity: the phase is carried entirely by the scheduled tick plus `powered`.

## 5. Interaction

**One interaction: right-click.** It walks the eight settings in order and wraps around.

| # | `delay` | `pulse` | Output |
| --- | --- | --- | --- |
| 1 | 1 | square | 1 rt on, 1 rt off |
| 2 | 2 | square | 2 on, 2 off |
| 3 | 3 | square | 3 on, 3 off |
| 4 | 4 | square | 4 on, 4 off |
| 5 | 1 | pulse | 1 on, 1 off — identical to setting 1 |
| 6 | 2 | pulse | 1 on, 3 off |
| 7 | 3 | pulse | 1 on, 5 off |
| 8 | 4 | pulse | 1 on, 7 off |

Settings 1 and 5 produce the same signal, because a 50 % duty cycle over a two-tick period *is* a
one-tick pulse. They differ only in the glyph on the block, so the cycle reads as eight steps but
behaves as seven. Collapsing them would make the two modes have different lengths and the cycle
harder to predict, which is not worth saving one click.

Each click plays `SoundEvents.COMPARATOR_CLICK` at `0.5 + delay × 0.05`, a tone higher in pulse
mode, and reports the mode on the action bar (`message.redstore.clock.mode.*`).

**There is no sneak interaction**, deliberately. See [plate §7](abstract-redstone-plate.md): vanilla
only delivers a sneaking click to a block when the player's hands are empty, so a setting bound to
it is unreachable exactly when a builder needs it. The block has no code that inspects the sneak
state.

## 6. Appearance

A repeater plate with **the waveform itself engraved on it**, and the delay shown the way a
repeater shows it: by the position of a sliding torch.

### 6.1 The mode badge

The right flank carries a **badge for the mode**, not a picture of the signal: a solid square for
the square wave, a thin bar for the 1-tick pulse.

```
square wave      1-tick pulse

   ##                ##
   ##                ..

   the same 2 x 2 footprint, half the area
```

Same box, half the area, so the two read apart by weight alone without having to resolve a shape.
The bar keeps the square's top row, so switching mode takes a row away rather than moving the mark,
and it suggests "briefly on" without asserting anything the block cannot keep.

It sits at x = 11, y = 9 — on the right flank, level with the middle of the dial across the plate.

**Drawing the actual waveform was tried and abandoned**, twice. A glyph cannot know the delay
setting, so it drew the same three cycles whether the clock ran at one tick per phase or at four:
a timing diagram that lied about the timing, and one that stayed identical while the thing it
claimed to depict changed under it. Both modes also start high, so the first mark was the same in
each and the eye had to travel down the whole glyph to tell them apart. Earlier drafts drew the
marks as 2 × 1 and 1 × 1 dashes across a narrow column, which read as a dotted line rather than a
wave and differed only in the thickness of a dot.

The lesson is worth keeping: **a badge claims only that it is one mode and not the other, and that
is a claim the block can always honour.**

It is engraved in vanilla's redstone red: dark when the clock is off, bright when it is on, the
same way the repeater's own painted line behaves.

### 6.1.1 The dial

A five-pixel clock face in the vanilla clock item's gold sits on the plate's left flank, beside
the torch track, in every variant. The waveform says what the block *does*; the dial says what it
*is*, which matters when a plate family all shares the same silhouette. The same dial, four pixels
across, sits at the top corner of the inventory icon, in the gap left by the torch that was erased
from the repeater sprite.

### 6.2 Torches and delay

The model is **the vanilla repeater's, unchanged but for its textures**: a fixed torch at the
output end and a sliding one whose four positions are the four settings. A clock is a repeater loop
in a block, so it carries a repeater's torches.

That is what pushed the waveform off the middle of the plate: the torch track owns the centre, the
dial owns the left flank, so the waveform runs **down the right flank, vertically**.

### 6.3 Files

Four textures, all derived from `block/repeater` and `block/repeater_on`:

| File | Mode | Output |
| --- | --- | --- |
| `redstone_clock_top.png` | square | off |
| `redstone_clock_top_on.png` | square | on |
| `redstone_clock_top_pulse.png` | pulse | off |
| `redstone_clock_top_pulse_on.png` | pulse | on |

Twenty-four models — `delay` × `pulse` × `powered`, plus a locked variant per `delay` × `pulse` —
each the matching `repeater_<n>tick[_on][_locked]` model with nothing changed but its textures. A locked clock is always off, so the lit locked models are not generated and the
block state file points both `powered` values at the unlit ones. 128 block state variants.

The **inventory icon** is derived from `item/repeater.png`, vanilla's hand-drawn 3/4 view of this
exact plate, with the dial set into it. Both torches stay, because the block has both and the
recipe pays for both, and the drawn redstone line stays whole, as vanilla draws it. A 3D block model renders a 2-pixel plate as a sliver,
which is why vanilla draws repeaters and comparators as sprites and why this follows suit.

### 6.4 The lit top face

The output state lights **both** the torch and the engraved waveform, because the lit base texture
comes free with the derivation and a plate is normally seen from above, where the torch is the
least visible part of it. This is also exactly what a vanilla repeater does with its own top
texture, so it costs no new rule.

## 7. Recipe

Shaped, yields 1:

```
redstone_torch  redstone_dust  redstone_torch
      .             clock            .
 smooth_stone   smooth_stone   smooth_stone
```

**It is the repeater's recipe with a clock under it**, which is what the block is. Read as a parts
list ([plate §10](abstract-redstone-plate.md)): two torches because the block has two — fixed and
sliding, like a repeater — the dust for the waveform engraved on the plate, the vanilla clock for
the dial, and three smooth stone for the plate.

The quartz an earlier draft asked for appeared nowhere on the block and is gone.

Cost: 4 gold ingots (inside the clock), 2 redstone torches, 1 redstone dust and 3 smooth stone.

Unlock trigger: `has(Items.CLOCK)`.

## 8. Vanilla equivalence

Required by design rule 1: a 2N-redstone-tick square clock is a loop of two repeaters set to N with
a lever to break it; the pulse variant is the same loop with a torch-and-repeater pulse shortener
on the output. Both are strictly larger and neither is faster.

## 9. Java mapping

```java
public class RedstoneClockBlock extends RedstonePlateBlock {
    public static final IntegerProperty DELAY   = BlockStateProperties.DELAY;   // 1..4
    public static final BooleanProperty PULSE   = BooleanProperty.create("pulse");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private int phaseLengthTicks(BlockState state, boolean onPhase) {
        int n = state.getValue(DELAY);
        if (!state.getValue(PULSE)) return 2 * n;      // square: N rt each phase
        return onPhase ? 2 : 4 * n - 2;                // pulse: 1 rt on, 2N-1 rt off
    }
}
```

Beyond the inherited overrides, the clock overrides `neighborChanged` (start/stop on the side
lock inputs), `onPlace` and `tick`.

## 10. What 26.3 actually provides

Beyond what [filter-hopper.md](filter-hopper.md) §8 already records:

| Expectation | What 26.3 actually does |
| --- | --- |
| `PushReaction.DESTROY` | Renamed to **`PushReaction.POPPED`**. |
| `Player#displayClientMessage(Component, boolean)` | Gone. The action bar is `ServerPlayer#sendSystemMessage(Component, boolean)`, so the call needs a `ServerPlayer` check. |
| `tick(BlockState, Level, …)` | Takes a **`ServerLevel`**: `tick(BlockState, ServerLevel, BlockPos, RandomSource)`. |
| `neighborChanged(…, Block, BlockPos, boolean)` | Now takes an `Orientation`: `neighborChanged(BlockState, Level, BlockPos, Block, Orientation, boolean)`. |
| `updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos)` | Now `updateShape(BlockState, LevelReader, ScheduledTickAccess, BlockPos, Direction, BlockPos, BlockState, RandomSource)`, with `ScheduledTickAccess` in `net.minecraft.world.level`. |

Confirmed as written: `HorizontalDirectionalBlock.FACING`, `BlockStateProperties.DELAY` (1–4) and
`POWERED`, `Block.box`, `Block.canSupportRigidBlock`,
`SignalGetter#getControlInputSignal(BlockPos, Direction, boolean)`,
`Level#scheduleTick(BlockPos, Block, int)`, `Level#getBlockTicks().hasScheduledTick(BlockPos, T)`,
`SoundEvents.COMPARATOR_CLICK`, and that a plate needs no `codec()`.
