# Roadmap

Phases are ordered so that each one leaves the mod in a playable, shippable state.

## Phase 0 — Skeleton

* Gradle build with Loom 1.17 / Gradle 9.6.0 / Java 25, no remapping.
* `fabric.mod.json`, `Redstore` + `RedstoreClient` entry points, `Redstore.id(...)` helper.
* Empty registry holder classes, creative tab, datagen entry point.
* The derived-asset pipeline, `generateAssets`; see [conventions.md](conventions.md) §10.
* Config record, codec, loader and `/redstore reload`.
* **Done when:** the mod loads on a dedicated server and a client and shows an empty creative tab.

## Phase 1 — Logic gates

* `RedstonePlateBlock` (the shared base), `LogicGateBlock`, `GateOperation`, the three
  registrations, `facing`/`inverted`/`power` states.
* Right-click inversion, 1-redstone-tick scheduling, strong front output.
* 12 top textures, 24 models via a datagen template, blockstate files, loot tables, recipes, tags,
  both language files.
* **Done when:** AND/OR/XOR and their negations behave exactly like their vanilla torch-and-repeater
  equivalents, with one redstone tick of delay, in a side-by-side test build.

## Phase 2 — withdrawn

Analog gates were this phase. They were implemented and removed again: `max` is a dust merge in
vanilla, every negation is a single subtract comparator, and `|a − b|` is one comparator whenever
the sign is known — so the mode duplicated the comparator while doubling the click cycle for
everyone. Parked in [ideas/](ideas/), one file per gate.

## Phase 3 — Redstone clock

* `RedstoneClockBlock`, `delay`/`pulse`/`powered` states, self-rescheduling ticks, back-face stop
  input, both duty-cycle modes and the `clock.allowPulseMode` switch.
* Extends `RedstonePlateBlock` and reuses the plate model template from phase 1; only two new
  textures and a sliding torch element.
* **Done when:** a clock at each of the four settings measures the same period as the equivalent
  vanilla repeater loop, stops instantly on a lever, and resumes with a full ON phase.

## Phase 4 — Filter hopper

* Block, block entity, access widener, vanilla hopper logic reuse.
* Whitelist filtering with a real filter item (no toggles yet), menu, screen, GUI sprite.
* Then the blacklist and strict-matching buttons, `ContainerData` sync, config switches.
* **Done when:** a single filter hopper replaces a vanilla sorter cell in a working item sorter, at
  the same throughput.

## Phase 5 — Chunk loader

* Block, block entity, `ChunkLoaderManager`, `ChunkLoaderSavedData`.
* Deferred ticket acquisition/release, ownership bookkeeping, startup reconciliation.
* Placement limits, dimension whitelist, status message, particles.
* **Done when:** a hopper clock in a loader's chunk keeps running with no player in the dimension,
  survives a full server restart, and breaking the block stops it — while an unrelated
  `/forceload`ed chunk is left untouched.

## Phase 6 — Polish

* `/redstore loaders list|count`.
* Optional mixin into `RedStoneWireBlock#shouldConnectTo` so dust does not visually connect to a
  gate's unused back face.
* `BlockEntityRenderer` showing the filter item on the filter hopper's sides.
* Emissive overlay on the chunk loader core.
* In-game documentation pass: item tooltips summarising each block's rules.
* README, screenshots, Modrinth page.

## Explicit non-goals

The mod stays inside its design philosophy. These are deliberately **not** planned:

* Wireless redstone, cross-dimensional links, or anything that has no vanilla equivalent.
* Item transport faster than a vanilla hopper, or storage larger than a vanilla container.
* Chunk loaders that make mobs spawn without a player — that would be new behaviour, not a
  simplification.
* Gate delay settings, gate locking, or more than two inputs (chain gates instead).
* Automation of the filter itself (a hopper feeding the filter slot to switch filters at runtime).
* Any client-side rendering feature that changes what the world simulates.

## Possible later additions (not committed)

* A NOT/buffer plate (one input, one output) — currently covered by an inverted OR gate with one
  input, so it is redundant.
* A filter hopper minecart.
* A "gate" variant that reads its two inputs from above and below instead of the sides, for
  vertical circuits.
* A clock setting beyond 4 redstone ticks, or a phase-offset control for synchronising several
  clocks — both would need a rule vanilla does not already have.
