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

16 states per gate: four facings, inverted or not, powered or not.

## 5. Interaction

One right-click toggles `inverted`, which is the gate's only setting: AND ↔ NAND, OR ↔ NOR,
XOR ↔ XNOR. There is no sneak interaction; see [plate §7](abstract-redstone-plate.md).

The click plays `SoundEvents.COMPARATOR_CLICK` at `0.55` when turning inversion on and `0.5` when
turning it off, and sends no message: the negation bubble on the plate already says which form the
gate is in.

## 6. Appearance

**A comparator with a metal core.** Three redstone torches — one at each input flank and one at the
output — the plate's painted redstone line rubbed out, and a panel of the gate's own metal set into
the stone between them.

The torch count is not decoration: it is the block's wiring diagram. Two in, one out, arranged where
the signals actually are, so a gate can be read from above without knowing which block it is.

### 6.1 The metal panel

Vanilla's idiom for "the same plate doing a different job" is an inlay, not a symbol: the comparator
is the repeater's plate with a piece of quartz set into it. Each gate follows that with the metal
its recipe calls for — iron, copper or gold — so the block wears what it is made of.

The panel is 6 × 6 pixels at x = 5, which centres it exactly, drawn in three tones taken from the
ingot's own texture: light on the top half, mid on the bottom, and a dark border all round. It was
eight wide until the torches moved inward and needed the room.

That border is what makes the design work at all. Iron is `#D8D8D8` against a plate of `#BBBBBB`
to `#C5C5C5` — barely twenty levels brighter, and invisible as a flat patch. What separates the
panel from the stone is its outline and its flatness, not its hue, which is the same reason
vanilla's pale quartz reads against pale stone.

A consequence worth recording: **the three gates differ only in colour.** There is no symbol to
fall back on, so they are harder to tell apart in the dark, and impossible for a player who cannot
distinguish the three metals. Letters were tried and rejected — no vanilla block uses text, and a
letter never stops looking like a decal — but this is the cost of that decision.

### 6.2 The marker

Inversion is drawn as a **bubble**: a 4 × 3 ring between the output torch and the panel, which is
as close to the output as the torch leaves room for. It is the standard mark in any logic diagram,
carved in the metal's dark tone rather than in redstone red, because a gate's recipe contains no
redstone dust and nothing on its face may imply an ingredient that is not there (see
[plate §10](abstract-redstone-plate.md)).

### 6.3 What lights up

The **torches**, exactly as on a repeater, using vanilla's own lit and unlit torch textures. The
plate's face does not change at all, so a gate needs only **two** top textures — plain and
inverted — and four models, the two textures against lit and unlit torches. Twelve models and six
textures for the three gates, plus one icon each, all produced by the `generateAssets` Gradle task.

Torch geometry is the repeater's own torch element copied three times and moved: to `x−4, z+5` for
the left input, `x+4, z+5` for the right, and left where it is for the output. In a lit model each
copy drags its six glow quads along with it.

The input torches stop two pixels short of the plate's edge. Flush against it they covered the
darker shading along the rim — the part of the top texture that reads as the plate's side when you
look at it from an angle — and the plate lost its edge.

The **icon** is the **comparator's** 3/4 sprite, not the repeater's, because the comparator already
draws three torches and that is what a gate has. Its painted redstone line is rubbed out, as on the
block, and a chip of the gate's metal goes where the block carries its panel.

The block state file has 16 variants per gate: `facing` × `inverted` × `powered`.

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
