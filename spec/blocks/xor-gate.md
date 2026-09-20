# XOR gate

`redstore:xor_gate`

On when the inputs disagree. The two-switch staircase light, and the core of any adder or comparator circuit; inverted, it is an equality detector.

**Extends:** [abstract-logic-gate.md](abstract-logic-gate.md), which extends
[abstract-redstone-plate.md](abstract-redstone-plate.md). Everything not stated in this file is
inherited: the repeater-sized plate, the two side inputs read with comparator-side semantics, the
front-only strong output, the fixed 1-redstone-tick delay, the right-click inversion and the loot
table.

## 1. Defined members

| Member | Value |
| --- | --- |
| Identifier | `redstore:xor_gate` |
| Display name (en_us) | XOR Gate |
| Display name (es_es) | Puerta XOR |
| Inverted form | XNOR |
| Inlaid panel | gold, `#FAD64A` |
| `GateOperation` constant | `XOR` |
| Recipe metal | Gold ingot |
| Extra tags | none beyond `redstore:logic_gates` |

## 2. Evaluation

```java
boolean test(int a, int b) { return ((a > 0) ^ (b > 0); }
```

Inputs are booleans, `A = a > 0` and `B = b > 0`; the output is `A ^ B`, or its negation when the
gate is inverted.

| A | B | XOR output | XNOR output |
| --- | --- | --- | --- |
| 0 | 0 | 0 | 15 |
| 0 | 15 | 15 | 0 |
| 15 | 0 | 15 | 0 |
| 15 | 15 | 0 | 15 |

Arithmetic on signal strengths is the comparator's job, not a gate's. An analog variant of this
gate — `abs(a - b)` — was specified and implemented, then withdrawn; it is parked in
[../ideas/analog-xor-gate.md](../ideas/analog-xor-gate.md).

## 3. Recipe

Shaped, yields 1 — the comparator's recipe with its quartz swapped for an ingot:

```
      .        redstone_torch        .
redstone_torch  gold_ingot   redstone_torch
 smooth_stone    smooth_stone    smooth_stone
```

Three torches, one gold ingot, three smooth stone — a parts list of the block, which is the
rule ([plate §10](abstract-redstone-plate.md)). Unlock trigger: `has(Items.GOLD_INGOT)`.

## 4. Textures

Two 16 × 16 top textures: the gold panel inlaid in the bare plate, with and without the
inherited negation bubble.

| File | Negation bubble |
| --- | --- |
| `xor_gate_top.png` | – |
| `xor_gate_top_inverted.png` | yes |

One texture serves both the lit and the unlit model, because what lights up is the pair of torches,
not the plate. Four block models — the two textures against lit and unlit torches — all derived
from the repeater's, with its two torches moved to the front corners. Sides and bottom reuse
`minecraft:block/smooth_stone`. The icon is `item/xor_gate.png`.

## 5. Inherited behaviour, for reference

| | |
| --- | --- |
| Inputs | left and right faces, comparator-side reading rule — [plate §4](abstract-redstone-plate.md) |
| Output | front face only, strong power — [plate §5](abstract-redstone-plate.md) |
| Delay | 1 redstone tick, fixed — [gate §3](abstract-logic-gate.md) |
| Right-click | toggles `inverted` — [gate §5](abstract-logic-gate.md) |
| Block state | `facing`, `inverted`, `powered` — [gate §4](abstract-logic-gate.md) |
| Tags | `redstore:logic_gates`, `redstore:redstone_plates`, `minecraft:mineable/pickaxe` |
| API checklist | [plate §14](abstract-redstone-plate.md) |
