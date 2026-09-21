# Logic gate — abstract spec

> **This is an abstract spec.** It describes no block. It defines what the three two-input logic
> gates share; each gate is specified in its own file.

**Extends:** [abstract-redstone-plate.md](abstract-redstone-plate.md) — form, placement,
orientation, signal reading rule, output, interaction grammar, model template, recipe principle, loot
and tags all come from there and are not repeated here.

**Subclasses:** [and-gate.md](and-gate.md) · [or-gate.md](or-gate.md) · [xor-gate.md](xor-gate.md)

**In Java a gate extends vanilla's `DiodeBlock`**, because a gate *is* a diode: a flat plate that
reads redstone, waits a tick and emits from its front. The spec hierarchy and the Java hierarchy
part company here — the plate above is a spec, not a base class — and §9 sets out what that
inheritance supplies and what follows from it. Several things a builder can rely on, including the
strength of the output below, are consequences of it rather than rules this mod wrote.

## 1. Inputs and output

A gate has **two inputs, both on side faces**, and one output at the front. The back face is not an
input and never carries a signal.

```
                north  =  OUTPUT (front)
                  ^
                  |
   west  A ---> [ & ] <--- B  east
                  |
                south  =  unused (back)
```

| Input | Face |
| --- | --- |
| A | `facing.getCounterClockWise()` — left when looking along the output |
| B | `facing.getClockWise()` — right |

Both are read with the base's rule (comparator side semantics, see
[abstract-redstone-plate.md](abstract-redstone-plate.md) §4):

```java
int a = readFace(level, pos, facing.getCounterClockWise());
int b = readFace(level, pos, facing.getClockWise());
```

All three operations are commutative, so A and B are interchangeable; the names exist only for
documentation.

### 1.1 The output is strong

A gate **strongly powers the block it faces**, exactly as a repeater or a comparator powers its
target. The consequence is the one builders care about: put a solid block in front of a gate and
that block becomes a power source in its own right, so dust running along it carries the signal,
and a piston, door, lamp or dropper touching it fires. Driving a piston straight off a gate needs
no repeater in between.

A weak output would have made the gates second-class next to the vanilla components they sit
beside, and it is not what this mod would have chosen — it simply comes with being a real diode
(§9). [abstract-redstone-plate.md](abstract-redstone-plate.md) §5 states the same rule for the
whole plate family, and §5.1 there records the one cosmetic wart it brings: dust draws itself as
connected to a gate's unused faces even though nothing ever comes out of them.

## 2. Evaluation contract

Each gate defines one pure predicate of its two inputs, and the block applies the inversion:

```java
boolean test(int a, int b);
```

| `inverted` | Output |
| --- | --- |
| `false` | `test(a, b)` |
| `true` | `!test(a, b)` |

* The inputs are booleans: `A = a > 0`, `B = b > 0`, and the output is 15 or 0. **Gates are
  digital.** Arithmetic on signal strengths belongs to the comparator, which is where vanilla
  keeps it, and a gate that also did arithmetic would blur a line vanilla draws clearly.
* The inverted forms of OR and XOR **output 15 with no inputs at all**, exactly like a vanilla
  redstone torch on an unpowered block. This is intended and must not be special-cased.

An analog mode — `min`, `max` and `|a − b|` on the raw strengths — was specified, implemented and
then withdrawn, because almost all of it duplicates what one comparator already does in one block,
and because carrying it doubled the click cycle for everyone. The three variants are parked in
[../ideas/](../ideas/), one file each, with their use cases and their vanilla cost.

## 3. Timing

* Fixed delay of **1 redstone tick = 2 game ticks**, like a repeater on its minimum setting. There
  is no delay setting on a gate.
* Scheduling is vanilla's, not ours: on any neighbour change, placement or click the block goes
  through `checkTickOnNeighbor`, which compares the answer with the current output and schedules a
  tick at the delay when they differ and nothing is already due. The tick priorities that make
  diodes resolve deterministically against each other come with it.
* Consequences, all intentional: gates cannot produce 0-tick pulses, a chain of *n* gates takes
  *n* redstone ticks, and a loop of gates oscillates at a stable frequency instead of locking up
  the scheduler.
* **A pulse shorter than the delay is not lost.** The gate turns on and schedules its own turn-off,
  so the output is one full delay long however brief the input was — a repeater does exactly this,
  and a gate does it for the same reason: it is the same state machine.
* **Vanilla equivalent:** any of the six gates is a torch-and-repeater assembly of at least 3 × 3,
  and none of them is faster than this.

## 4. Block state

Adds to the base's `facing`:

| Property | Type | Values | Meaning |
| --- | --- | --- | --- |
| `inverted` | `BooleanProperty` | `false` default | Negated output (NAND / NOR / XNOR). |
| `powered` | `BooleanProperty` (`DiodeBlock.POWERED`) | `false` default | Whether the gate is emitting; also the lit flag. Inherited from the diode. |
| `input_left` | `BooleanProperty` | `false` default | Whether the left flank is carrying a signal. |
| `input_right` | `BooleanProperty` | `false` default | The same on the right. |

64 states per gate: four facings × inverted × powered × the two inputs.

The two input flags are **face, not logic.** Nothing reads them back: `shouldTurnOn` asks the level
directly, as it always did, so a stale flag can never change what the gate answers. They exist so
that the block shows what it is being told as well as what it is saying.

They are written with `UPDATE_CLIENTS` and not `UPDATE_ALL`. Which torches are lit is a picture,
and telling the neighbours about it would put a block update on the wire every time any input
anywhere changed — on a block meant to be placed in the hundreds.

## 5. Interaction

One right-click toggles `inverted`, which is the gate's only setting: AND ↔ NAND, OR ↔ NOR,
XOR ↔ XNOR. There is no sneak interaction; see [plate §7](abstract-redstone-plate.md).

The click plays `SoundEvents.COMPARATOR_CLICK` at `0.55` when turning inversion on and `0.5` when
turning it off, and sends no message: the negation bubble on the plate already says which form the
gate is in.

## 6. Appearance

**A comparator with a metal core**, and that is literal: the top texture *is*
`block/comparator.png` with its quartz recoloured to the gate's own metal, on the comparator's
**own models**. Three redstone torches, two at the back corners and one at the front, exactly where
vanilla puts them, and the painted line running between the back two, exactly as vanilla paints
it.

The torch count is what the block says about itself: three torches is the comparator's silhouette,
and a gate is a comparator-shaped thing — two inputs, one output, one tick more delay than a
repeater. It is not a wiring diagram. An earlier version moved the two rear torches out to the
flanks, to sit where the inputs are; against the comparator's plate that read as a mistake rather
than as a diagram, and vanilla's own two torches are not at a comparator's inputs either.

### 6.1 The metal inlay

Vanilla's idiom for "the same plate doing a different job" is an inlay, not a symbol: the comparator
is the repeater's plate with a piece of quartz set into it. A gate does not imitate that idiom —
**it takes the setting itself.** The top texture is the comparator's, and the four warm tones its
quartz is drawn in (`#EBDED4`, `#DDCBBE`, `#D3C7B9`, `#C5B8A9`, rows 6 to 10) are replaced by the
gate metal's own colour — one tone, from its ingot — **at the four brightnesses the quartz has**.
Iron, copper or gold, sitting in the shape vanilla already uses for a stone set into a redstone
plate.

The shading has to be the quartz's, and that is not a refinement. Four tones picked out of an
ingot by hand look right in a table and wrong on the block: quartz is drawn across 38 levels of
brightness, while iron's ingot spans 74, copper's 88 and gold's 116. The darkest tone is the one
that draws the inlay's outline, and that far below the body it stops being depth and becomes a
drawn line — which the comparator's own outline, barely visible, never is. Scaling one colour by
the quartz's own brightnesses keeps the hue of the ingot and the contrast of vanilla.

An earlier version drew a 6 × 6 panel of our own on the repeater's plate instead, with a dark
border to separate it from the stone. It worked, and it was still a badge we invented: a bar of
metal lying on a plate rather than a stone set into one. Recolouring the real thing is both truer
and cheaper — there is no shape to design, no border to tune, and no size to re-fit when the
torches move.

It also travels. A resource pack that redraws the comparator is followed for free, at whatever
resolution, as long as it keeps vanilla's palette — and Faithful 64x keeps it to the colour, so the
inlay there is Faithful's own 469-pixel quartz in iron rather than ours enlarged four times.

Nothing else on the plate is touched. An earlier version also rubbed out the painted redstone
line, on the argument that a gate has no signal travelling that path — but on the comparator's
plate that line runs from one rear torch to the other, and on a gate those two torches are the
inputs. It joins the two things that feed the block, which is what it appears to do on a comparator
too, so it stays.

(That rub-out is also where this design's one silent bug lived: it found the line by colour, copper
and gold fall inside that rule, and running it after the recolouring erased the inlay on two of the
three gates and left iron alone because iron is grey. It is recorded in
[../conventions.md](../conventions.md) §10.3 because the lesson outlives the code.)

A consequence worth recording: **the three gates differ only in colour.** There is no symbol to
fall back on, so they are harder to tell apart in the dark, and impossible for a player who cannot
distinguish the three metals. Letters were tried and rejected — no vanilla block uses text, and a
letter never stops looking like a decal — but this is the cost of that decision.

### 6.2 The marker

Inversion is drawn as a **bubble**: a 4 × 3 ring in the plate's **top right corner**, at x = 11,
y = 2. It is the standard mark in any logic diagram, carved in the metal rather than in redstone
red, because a gate's recipe contains no redstone dust and nothing on its face may imply an
ingredient that is not there (see [plate §10](abstract-redstone-plate.md)).

The corner is the only place it fits. The comparator's composition spends the middle of the plate
on the inlay, the front on the output torch and the back on the painted line; the bubble sat
between the torch and the inlay while the plate was the repeater's, and there is no such gap here.

It is drawn in a **fifth** tone, the ingot's darkest, and not in the inlay's own dark one. Iron's
darkest inlay tone is `#A3A3A3` against a plate of `#BBBBBB`, and a marker that faint is no marker;
the ingot's `#7E7E7E` reads.

At 16 × 16 it is that 4 × 3 glyph; at any finer resolution it is drawn as a **circle**, centred and
sized in units of the small design so the two cannot drift. A ring made of four-pixel blocks is
what a scaled glyph gives, and on a 64 × 64 plate it is the one mark that looks unfinished. See
[../conventions.md](../conventions.md) §10.2.1.

The mark is not the only thing that says a gate is inverted, and it is the less useful of the two:
the output torch follows the output, so an inverted gate with no input stands there with its front
torch lit. That is the same tell a redstone torch on an unpowered block gives, and it is visible
from further away than a four-pixel ring.

### 6.3 What lights up

**The three torches light independently.** Each flank's torch follows that flank's input, and the
front torch follows the output — so a gate at a glance says what it has been given and what it has
decided. A player who cannot tell iron from copper in a dark corridor can still see that the left
input is live and the gate is not answering.

This is the whole point of the two input flags, and it is worth being clear that it is **not** the
inversion mark by another name: the flanks say what is arriving, the front says what is leaving,
and inversion only changes the relationship between them.

The plate's face never changes, so a gate needs **two** top textures — plain and inverted — and
**sixteen** models: two faces against each of the eight lit/unlit combinations of three torches.
Forty-eight models and six textures for the three gates, plus one icon each, all produced by the
`generateAssets` Gradle task. The block state file has 64 variants per gate.

**Every torch comes from a vanilla model, unmoved.** `comparator.json` holds the three unlit
torches and `comparator_on_subtract.json` the three lit ones — the second because it is the one
state in which a comparator has all three lit, each dragging the six glow quads a lit torch needs.
A model is the slab plus one of the two versions of each torch; not a single coordinate is
computed.

Elements are taken by index, because glow quads cannot be told apart by geometry — two of them
occupy the same box — and each index is then checked against the corner it should have. A
reordering in a future vanilla model fails the build rather than quietly putting a torch somewhere
else.

The **icon** is the **comparator's** 3/4 sprite, not the repeater's, because the comparator already
draws three torches and that is what a gate has. It diverges from the block in two ways, both
forced by size. The sprite draws a plate five pixels deep and **no quartz at all**, so there is
nothing to recolour and the metal is drawn instead: the smallest shape that reads as a stone set
into the plate rather than a bar lying on it, five pixels in the shape of a diamond. And its
painted line *is* rubbed out, unlike the block's, because those same five pixels are the only place
the metal can go.

Left is `+x`: a model with no rotation is the gate facing south, whose left flank —
`FACING.getCounterClockWise()` — is east. The block state file rotates it from there.

## 7. Recipe

```
.  T  .          T = redstone torch
T  M  T          M = the gate's metal ingot
S  S  S          S = smooth stone
```

**It is the comparator's recipe with the quartz swapped for an ingot**, which is exactly what the
block is: the comparator's three torches and stone plate, with a metal core instead of a quartz
one. Read as a parts list, which is the rule ([plate §10](abstract-redstone-plate.md)): three
torches because the block has three, one ingot for the panel, three smooth stone for the plate.

## 8. Tags

`redstore:logic_gates` in addition to the base's `redstore:redstone_plates` and
`minecraft:mineable/pickaxe`. The clock is deliberately **not** in `redstore:logic_gates`.

## 9. Java mapping

```java
public class LogicGateBlock extends DiodeBlock {
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

    private final GateOperation operation;   // AND / OR / XOR

    protected int getDelay(BlockState state) { return 2; }
    protected boolean sideInputDiodesOnly()  { return false; }
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state);
}
```

**A gate extends vanilla's `DiodeBlock`, because a gate is a diode.** That single decision supplies
the plate's shape, `canSurvive`, `getStateForPlacement`, the front-only strong output, the delay
handling, the whole `neighborChanged` → `checkTickOnNeighbor` → `shouldTurnOn` → `tick` state
machine with its scheduling priority, and membership of the set of blocks vanilla will accept as a
diode. The mod writes two hooks and a state property.

Three consequences worth stating:

* **A gate can lock a repeater**, and a comparator's side can read one. A look-alike that merely
  imitated a diode could not, because vanilla decides with `getBlock() instanceof DiodeBlock`.
* **Timing is vanilla's**, including how a pulse shorter than the delay is handled: the state
  machine turns the output on and schedules the turn-off itself, so a gate stretches a short pulse
  exactly as a repeater does. An earlier hand-written version scheduled differently.
* **The output is a boolean.** `POWERED` replaces the `power` 0–15 of the hand-written version,
  which only ever wrote 0 or 15 anyway. It also means the parked analog idea could not return this
  way: a diode stores an analog output in a block entity, as the comparator does, and that is a
  real cost on a block placed in the hundreds — see [../ideas/README.md](../ideas/README.md).

The three gates are three instances of this one class, so they need no subclasses in Java, and no
block entity.

## 10. What every gate must define

| # | Member |
| --- | --- |
| 1 | Identifier and display names |
| 2 | Its IEC symbol and its four top textures |
| 3 | `test`, with the resolved truth table |
| 4 | The metal it is inlaid with and crafted from |

Everything else is inherited.
