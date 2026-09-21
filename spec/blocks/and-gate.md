# AND gate

`redstore:and_gate`

Both inputs must be on. The most common building block of interlocks and safety gates.

**Extends:** [abstract-logic-gate.md](abstract-logic-gate.md), which extends
[abstract-redstone-plate.md](abstract-redstone-plate.md). Everything not stated in this file is
inherited: the repeater-sized plate, the two side inputs read with comparator-side semantics, the
front-only strong output, the fixed 1-redstone-tick delay, the right-click inversion and the loot
table.

## 1. Defined members

| Member | Value |
| --- | --- |
| Identifier | `redstore:and_gate` |
| Display name (en_us) | AND Gate |
| Display name (es_es) | Puerta AND |
| Inverted form | NAND |
| Inlaid metal | iron — `#D4D4D4`, mark `#7E7E7E`, shaded at the quartz's four brightnesses |
| `GateOperation` constant | `AND` |
| Recipe metal | Iron ingot |
| Extra tags | none beyond `redstore:logic_gates` |

## 2. Evaluation

```java
boolean test(int a, int b) { return (a > 0 && b > 0; }
```

Inputs are booleans, `A = a > 0` and `B = b > 0`; the output is `A && B`, or its negation when the
gate is inverted.

| A | B | AND output | NAND output |
| --- | --- | --- | --- |
| 0 | 0 | 0 | 15 |
| 0 | 15 | 0 | 15 |
| 15 | 0 | 0 | 15 |
| 15 | 15 | 15 | 0 |

Arithmetic on signal strengths is the comparator's job, not a gate's. An analog variant of this
gate — `min(a, b)` — was specified and implemented, then withdrawn; it is parked in
[../ideas/analog-and-gate.md](../ideas/analog-and-gate.md).

## 3. Recipe

Shaped, yields 1 — the comparator's recipe with its quartz swapped for an ingot:

```
      .        redstone_torch        .
redstone_torch  iron_ingot   redstone_torch
 smooth_stone    smooth_stone    smooth_stone
```

Three torches, one iron ingot, three smooth stone — a parts list of the block, which is the
rule ([plate §10](abstract-redstone-plate.md)). Unlock trigger: `has(Items.IRON_INGOT)`.

## 4. Textures

Two 16 × 16 top textures: the comparator's plate with its quartz recoloured to iron, with
and without the inherited negation bubble.

| File | Negation bubble |
| --- | --- |
| `and_gate_top.png` | – |
| `and_gate_top_inverted.png` | yes |

One texture serves every model, because what lights up is the torches, not the plate. Sixteen
block models — the two textures against the eight lit/unlit combinations of three torches — each
composed out of vanilla's own comparator models. Sides and bottom reuse
`minecraft:block/smooth_stone`. The icon is `item/and_gate.png`.

## 5. Inherited behaviour, for reference

| | |
| --- | --- |
| Inputs | left and right faces, comparator-side reading rule — [plate §4](abstract-redstone-plate.md) |
| Output | front face only, strong power — [plate §5](abstract-redstone-plate.md) |
| Delay | 1 redstone tick, fixed — [gate §3](abstract-logic-gate.md) |
| Right-click | toggles `inverted` — [gate §5](abstract-logic-gate.md) |
| Block state | `facing`, `inverted`, `powered`, `input_left`, `input_right` — [gate §4](abstract-logic-gate.md) |
| Tags | `redstore:logic_gates`, `redstore:redstone_plates`, `minecraft:mineable/pickaxe` |
| API checklist | [plate §14](abstract-redstone-plate.md) |
