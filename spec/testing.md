# Automated tests

**Status:** the list below is decided; none of it is built yet. This file is the specification for
the test suite, written the way every block here is written — before the code.

Nothing in this file records whether anything has been tried in game. That is a separate thing and
it is not what a test suite is for.

## 1. The two frameworks, and what each is actually for

Both are available in 26.3 and neither needs anything the project does not already depend on.

### 1.1 GameTest — behaviour, in a world

Vanilla ships the framework in `net.minecraft.gametest.framework`, and Fabric API already provides
the module (`fabric-gametest-api-v1`, on the classpath through `fabric-api`). Verified against the
version in use:

* A test is a **public, non-static method taking one `GameTestHelper` and returning void**,
  annotated with `net.fabricmc.fabric.api.gametest.v1.GameTest`, ending in `helper.succeed()`.
* The annotation carries `structure`, `maxTicks` (default **20**), `setupTicks`, `rotation`,
  `padding`, `skyAccess`, `required`, `manualOnly`, `maxAttempts`, `requiredSuccesses`,
  `environment` and `dimension`.
* The class holding the tests is listed under the **`fabric-gametest` entrypoint** in
  `fabric.mod.json`.
* A test runs in a structure placed in an empty world. The default is an 8 × 8 empty one
  (`fabric-gametest-api-v1:empty`); our own go in `data/redstore/gametest/structure/` as SNBT.
* It runs as a headless dedicated server with `-Dfabric-api.gametest`, and writes a JUnit XML
  report when `-Dfabric-api.gametest.report-file` is set.

This is the framework for **everything with a tick in it**: signals, delays, item movement,
chunk tickets. Most of this mod is behaviour, so most of the suite lives here.

### 1.2 JUnit — functions, with no world

`Bootstrap.tryDetectVersion()` and `Bootstrap.bootStrap()` exist and are what a plain JUnit test
calls before it touches registries, so `ItemStack`, block states and codecs can be used without a
server. Tests that need none of that — the colour arithmetic in `buildSrc/` — need no bootstrap at
all.

This is the framework for **everything with no tick in it**: truth tables, matching rules,
bookkeeping, codecs, and the derived-asset pipeline.

### 1.3 The rule for deciding which

> If the answer depends on **when**, it is a game test. If it depends only on **what**, it is a
> unit test.

`GateOperation.test(a, b)` is a function and belongs in JUnit. Whether the gate's output follows two
game ticks later is a game test. Testing the first in a world is slow and testing the second out of
one is impossible.

## 2. What cannot be tested automatically, and why

Worth stating up front so nobody goes looking for a way.

* **The chunk loader's headline claim** — a contraption keeps running with **no player in the
  dimension**, and **survives a full server restart** — is not reachable from a game test. The test
  runner loads the world itself and cannot restart the server mid-test. That criterion stays where
  it is, as the acceptance check in [roadmap.md](roadmap.md), and it is run by hand.
* **Anything about how a thing looks.** A game test can assert that a block state exists and that a
  model is not the missing-model placeholder; it cannot assert that the pearl reads as a sphere or
  that the cloth is the right teal. §6 lists the parts of the art that *are* mechanically checkable.

## 3. Unit tests — `buildSrc`, no game at all

The colour code is pure arithmetic on `int`s, which makes it the cheapest thing in the project to
test and the easiest to get quietly wrong.

| # | Test | What it protects |
| --- | --- | --- |
| B1 | `luminance` agrees with the Rec. 601 weights on the channel extremes. | The basis of every other colour operation. |
| B2 | `shade(colour, f)` scales each channel by `f` and clamps at 255, never wrapping. | A tone going dark instead of bright. |
| B3 | `dye(tones, colour)` maps the **brightest** input tone to `colour` itself, and preserves the input's luminance ordering. | The tablecloth losing its shading. |
| B4 | `inlay(metal)` normalises against the **average** of `QUARTZ`, not the brightest. | The gates' inlay escaping the plate's brightness — the bug that made it look like an outline. |
| B5 | `recolour` replaces only the exact tones in the map and leaves every other pixel identical. | Collateral damage to a texture. |

## 4. Unit tests — the derived asset pipeline

These run the real task against the Minecraft jar Loom has already downloaded. Slower than §3 and
still no game.

| # | Test | What it protects |
| --- | --- | --- |
| P1 | Every file the mod's block states and models reference exists in the task's output. | A blockstate pointing at a model nobody generates. |
| P2 | Every model the task writes is valid JSON, and each `#texture` reference resolves to something in `textures` or to a `minecraft:` path that exists in the jar. | A typo in a texture key. |
| P3 | **No tone of a replaced palette survives in an output texture.** For each map — the gems, the cloth, the quartz — assert the output contains none of the source tones. | Exactly the Faithful tablecloth bug: a tone the map did not name stayed red. |
| P4 | P3 again with a Faithful pack, **skipped when `faithful_pack` is not set**. | The same bug, in the resolution where it actually happened. |
| P5 | The output directory contains nothing the current run did not write. | The stale `chunk_loader_off.json` that was being packed into the jar. |

## 5. Unit tests — the mod

| # | Test | What it protects |
| --- | --- | --- |
| U1 | `GateOperation.test(a, b)` over the whole truth table, for all three operations. | The one piece of pure logic in the mod. |
| U2 | The same over strengths 0–15: any non-zero is true, and only 0 is false. | A gate treating strength 3 differently from strength 15. |
| U3 | `GateOperation.name(inverted)` returns `and`/`nand`, `or`/`nor`, `xor`/`xnor`. | The action-bar message naming the wrong mode. |
| U4 | **Every translation key the code builds exists in both `en_us.json` and `es_es.json`, and the two files have the same key set.** | A missing key showing as raw `message.redstore.…` in game. The language files are hand-written by decision ([conventions.md](conventions.md) §11), so nothing else checks them. |
| U5 | The filter rule, case by case against the table in [blocks/filter-hopper.md](blocks/filter-hopper.md) §2: empty filter accepts everything in both modes; loose accepts a damaged, enchanted or renamed variant; strict rejects each of them; blacklist inverts all of it. | The rule the whole block exists for. |
| U6 | `ChunkLoaderSavedData`, where the return values *are* the contract: `claim` returns true only for the **first** loader in a chunk and false for one already recorded; `unclaim` returns true only when the **last** one goes **and** the chunk is not marked as somebody else's; after `keepForSomeoneElse` the chunk is never given back. | Releasing a chunk somebody else forced, or leaking one of ours — the two ways this block goes wrong permanently. |
| U7 | `ChunkLoaderSavedData` survives a `CODEC` round-trip with both lists populated. | The record not surviving a restart. |
| U8 | Each block's state definition holds exactly the properties its spec lists, and the default state is the one the spec names. | A property added without the spec, or a default nobody meant. |
| U9 | Every block registered in `RedstoreBlocks` has a loot table, a recipe, an item model and a block state file in `src/main/generated`. | A block added without its datagen being re-run. |

## 6. Game tests — the shared plate rules

These apply to every plate and are worth testing once rather than per block.
See [blocks/abstract-redstone-plate.md](blocks/abstract-redstone-plate.md) §4 and §5.1.

| # | Test |
| --- | --- |
| S1 | A gate's front output is **strong**: a solid block in front of it powers dust sitting on that block. |
| S2 | Dust connects to a gate's two flanks and its front, and **not** to its back. |
| S3 | Dust connects to a clock's output face and to nothing else. |
| S4 | A Redstore gate **locks a vanilla repeater** pointing into its side — it is a real `DiodeBlock`, and a look-alike could not. |
| S5 | Every block state of every block resolves to a model that is not the missing-model placeholder. *(Client-side; whether this can be asserted from a client game test has to be checked before it is promised.)* |
| S6 | Each block's harvest matches what its properties and tags promise: the plates are `instabreak` and drop to a bare hand; the chunk loader is the only one with `requiresCorrectToolForDrops` and `needs_diamond_tool`, and §10 covers it in full. |

## 7. Game tests — the logic gates

Per gate, so three times over, from [blocks/abstract-logic-gate.md](blocks/abstract-logic-gate.md).

| # | Test |
| --- | --- |
| G1 | The truth table: for each of the four input combinations, the output after the delay is `operation.test(a, b)`. |
| G2 | The same with `inverted = true`, giving NAND, NOR and XNOR. |
| G3 | The delay is **one redstone tick**: the output has not changed one game tick earlier, and has by the second. |
| G4 | An inverted OR or XOR with no inputs at all is **on**, like a torch on an unpowered block. |
| G5 | Right-clicking flips `inverted`, and the output follows after the delay. |
| G6 | `input_left` and `input_right` follow the two flanks, and neither one follows the other's. |
| G7 | The back face is not an input: a signal behind the gate changes nothing. |

## 8. Game tests — the redstone clock

From [blocks/redstone-clock.md](blocks/redstone-clock.md) §2 and §3.

| # | Test |
| --- | --- |
| K1 | A clock placed with no input **runs**, without being switched on. |
| K2 | For N ∈ {1, 2, 3, 4} in square mode: the period is **2N redstone ticks**, on for N and off for N. |
| K3 | For the same N in pulse mode: the period is **2N**, on for exactly **1** redstone tick. |
| K4 | At N = 1 the two modes produce the same waveform, and not because of a special case. |
| K5 | A signal on **either** side stops it immediately: the output drops to 0 and `locked` becomes true. |
| K6 | Releasing the stop signal starts a **fresh on phase**, not the remainder of the interrupted one. |
| K7 | Right-click walks all eight settings and returns to the first. |

## 9. Game tests — the filter hopper

From [blocks/filter-hopper.md](blocks/filter-hopper.md) §2 and §2.1. Every row of the table in §2.1
is a test, because the whole point of implementing the filter as `canPlaceItem` is that it covers
all of them at once — which is worth proving rather than asserting.

| # | Test |
| --- | --- |
| H1 | An empty filter behaves as a vanilla hopper, in both whitelist and blacklist. |
| H2 | Whitelist with a filter set: a matching item goes in, a different one does not — once per insertion path: item entity from above, a hopper pushing in, a dropper pushing in, the hopper pulling from the container above, and a player putting it in. |
| H3 | Blacklist inverts every one of those. |
| H4 | Loose matching accepts a damaged, enchanted and renamed variant of the filter item; strict rejects all three. |
| H5 | **The filter never blocks the output**: a disallowed item already inside is still pushed on. |
| H6 | A hopper minecart underneath can still pull a disallowed item out. |
| H7 | The filter slot itself accepts any item. |
| H8 | It is otherwise a hopper: it respects the transfer cooldown, pushes into the container it faces, and is disabled by a redstone signal. |
| H9 | Breaking it drops its contents **and** the filter stack. |

## 10. Game tests — the chunk loader

From [blocks/chunk-loader.md](blocks/chunk-loader.md) §2. Everything here is about the ticket
record, which is the part a mistake leaves broken for ever.

| # | Test |
| --- | --- |
| C1 | Placing a loader forces its chunk. |
| C2 | Breaking it releases the chunk. |
| C3 | Switching it off releases the chunk; switching it on takes it again. |
| C4 | A chunk **already forced by something else** is not released when our loader is broken. |
| C5 | Two loaders in one chunk: breaking one leaves the chunk forced; breaking both releases it. |
| C6 | Reconciliation drops a record whose block is no longer there. |
| C7 | The chunk-load hook claims a loader the record has never heard of. |

| C8 | **The chunk is released whichever way the block goes.** Five paths, same assertion: broken in creative, broken in survival with a bare hand, with an iron pickaxe, with a diamond pickaxe, and removed with no player involved at all. |
| C9 | **The drop follows the tool, and the release does not follow the drop.** Bare hand and iron pickaxe leave nothing on the ground; the diamond pickaxe drops a `redstore:chunk_loader`. In all four the chunk is released just the same. |
| C10 | An explosion releases the chunk. Only the release is asserted — the loot table's `survives_explosion` makes the drop a dice roll, and a test that rolls dice is a test that fails on a Tuesday. |

C1–C10 are all readable within one test. The two things that are not — no player in the dimension,
and surviving a restart — are §2 of this file.

### 10.1 Why C8 and C9 are two rows and not one

Because they are two pieces of code. The release runs from `affectNeighborsAfterRemoval`, which
fires when the block state stops being a chunk loader; the drop comes from the loot table, which
vanilla consults only when the tool is good enough. Nothing enforces that they stay independent, and
a plausible future edit — moving the release into a drop handler, or gating it on a successful
harvest — would leave a chunk forced for ever every time somebody mined a loader with the wrong
pickaxe. That failure is silent, permanent and invisible until a server admin goes looking.

### 10.2 The four player paths are expressible

Checked against the version in use, so the list above is not promising something the framework
cannot do:

* `GameTestHelper#makeMockServerPlayerInLevel()` returns a real `ServerPlayer`.
* `ServerPlayer.gameMode` is a public field, and `ServerPlayerGameMode#changeGameModeForPlayer` and
  `#destroyBlock(BlockPos)` are both public — `destroyBlock` is the real survival break, tool tier,
  loot and all.
* The held tool is just `player.setItemInHand(...)` before the call.
* `GameTestHelper#destroyBlock(BlockPos)` is the fifth path: it calls
  `ServerLevel#destroyBlock(pos, false, null)`, so no drops and no player.
* `GameTestHelper#assertItemEntityPresent` and `#assertItemEntityNotPresent` settle C9.

### 5.1 One thing the mod has to give up to be testable

The filter rule is `FilterHopperBlockEntity#filterAccepts(ItemStack)`, which reads `strict` and
`blacklist` off the block entity it hangs on. Testing it therefore means building a block entity,
which means a position, a block state and a container — all of it scaffolding around four values.

It should become a **static function of `(filter, stack, strict, blacklist)`** with the block entity
calling it. That is not a change made for the test's sake: the rule genuinely does not depend on the
block, and the table in [blocks/filter-hopper.md](blocks/filter-hopper.md) §2 is written as a
function of exactly those four things. It is noted here because it is the one place where the suite
asks the mod to move, and that is better decided now than discovered halfway through writing U5.

## 11. Where the tests live

Game tests must not ship. A test class registered under the `fabric-gametest` entrypoint of the
mod's own `fabric.mod.json` would be in the released jar, along with its structures.

```
src/test/java/…        JUnit: sections 5
src/testmod/java/…     the game tests and their fabric.mod.json, its own source set
src/testmod/resources/data/redstore/gametest/structure/*.snbt
buildSrc/src/test/java/…   JUnit: sections 3 and 4
```

`src/testmod` is Fabric's own convention for this and Loom has the run configuration for it. The
exact wiring — the source set, the `gametest` run config inheriting `testmodServer`, the two system
properties — belongs to the phase that builds this, not to this file.

## 12. What a failing test has to say

A game test failure gives a block position in a throwaway world, which is worth very little by
itself. Every assertion carries a message naming the block, the state and the expected value, so
the report says *"and_gate at 2,1,3: inverted=false, inputs 1/0, expected off, was on"* rather than
*"assertion failed"*.
