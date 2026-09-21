# Redstore

A small Fabric mod for **Minecraft 26.3** that compresses redstone circuits which are already
possible in vanilla into single blocks, so that contraptions on a technical server stay readable
and compact.

Nothing here does anything vanilla cannot **in survival, without commands**. A logic gate is a
torch-and-repeater assembly in one cell; the filter hopper is the classic comparator sorter cell;
the chunk loader is an ender pearl stasis chamber without the pearl. The mod removes tedium, not
limits.

> **Early and unreleased.** All six blocks are written. The mod ID `redstore` is a working
> codename and the public name is not settled.

## What it adds

| Block | What it does |
| --- | --- |
| **AND / OR / XOR gate** | Repeater-sized plates with two side inputs and one front output, one redstone tick of delay. Right-click inverts them into NAND, NOR and XNOR. Each is crafted with, and wears a panel of, its own metal: iron, copper, gold. |
| **Redstone clock** | Emits a periodic signal. Right-click walks eight settings: 1 to 4 redstone ticks per phase, as a square wave or as a 1-tick pulse. Runs by default; a signal on either side stops it and shows the bedrock bar of a locked repeater. |
| **Filter hopper** | A hopper with one extra filter slot, plus whitelist/blacklist and strict/loose matching. The filter governs what may *enter*; whatever is inside can always leave. |
| **Chunk loader** | Keeps its own chunk force-loaded and fully ticking, across restarts, with nobody nearby. An enchanting table with an ender pearl where the book goes. Right-click switches it off and on; the pearl is there when it is working. |

### What a chunk loader does, and does not do

It uses the same force-loading vanilla's own `/forceload` uses, so it inherits vanilla's rules, and
two of them surprise people:

* **A force-loaded chunk is not a player.** Everything the game gates on a player being nearby stays
  gated: **no natural mob spawning and no monster spawners**. A dark room does not run. Everything
  driven by entity AI, block entities or random ticks does run, so an iron farm, a crop farm, a
  smelter or a hopper clock all keep going.
* **It reaches one chunk further than you would think, but not for everything.** In its own chunk
  everything runs. In the eight chunks around it, *blocks* run — furnaces, hoppers, redstone,
  crops — but *entities* are frozen: dropped items do not fall, minecarts and mobs do not move.
  Past that, nothing runs. So a machine crossing a chunk border usually needs no second loader;
  one that carries items or minecarts across does.
* Weather, time and random ticks behave exactly as in a chunk you are standing in.

## Requirements

* Minecraft **26.3**
* Fabric Loader **0.19.5** or newer
* **Fabric API** — required, for the block entity builder and the creative tab hook
* Java **25**

Install on both the client and the server: the mod adds blocks and a screen, so it is not
server-side only.

There is no configuration file. Every block behaves the same everywhere, and a server that does not
want one of them removes its recipe with a datapack.

## Building

**This mod is distributed as source.** Build it yourself:

```sh
./gradlew build        # jar in build/libs/
./gradlew runClient    # development client
```

A JDK 25 or newer is the only prerequisite — Gradle comes from the wrapper in the repository, and
Minecraft is downloaded by Loom. Minecraft has shipped unobfuscated since 26.1, so there are no
mappings to apply and Loom does not remap anything: mod dependencies use `implementation`, and the
artifact comes from the plain `jar` task.

The build also derives the mod's textures from your own copy of the game; see below.

## Layout

```
spec/                 the design, written before the code — see spec/README.md
src/                  the mod; client-only classes live in src/client
src/main/generated/   written by ./gradlew runDatagen — recipes, loot, tags, block states
buildSrc/             the generateAssets task, which derives the art from the vanilla jar
```

### The spec

Every block is specified before it is built, one file per block, with abstract specs holding what
the flat plate components share. `spec/ideas/` keeps what was considered and rejected, so the same
ground is not re-covered: the analog gate modes live there, built once and then withdrawn.

Start at **[spec/README.md](spec/README.md)**.

### The art

No texture in this mod is hand-drawn, and **none is stored in this repository**. Every one is a
vanilla texture with a small, precisely defined edit — the filter hopper is a hopper with the rim
of its mouth recoloured to the item frame's wood; a gate is the repeater's plate with its line
rubbed out and a panel of its metal set into it.

Those edits live in `buildSrc/` as code, and the Gradle task `generateAssets` applies them at build
time, reading **your own copy of Minecraft** and writing into `build/`. It runs automatically before
`processResources`, so both the jar and the development client get the assets with no extra step.

Two things follow from that, and both are the point. The edit stays reviewable in a diff and can be
replayed whenever Mojang retouches an original. And the repository never contains a modified
Mojang texture: what it holds is the code that describes the edit.

#### If you play with Faithful 64x

The same edits can be applied to [Faithful 64x](https://faithfulpack.net) instead, so the mod's
blocks are not the only thing in your world still at 16 × 16. Point the build at your own unpacked
copy of the pack:

```
./gradlew build -Pfaithful_pack="$HOME/.minecraft/resourcepacks/Faithful 64x - Release 15"
```

and the mod gains a resource pack, *Redstore for Faithful 64x*, which you switch on in the resource
pack screen. Leave the property out and nothing changes.

Faithful 64x is by HARYA_ and many others — <https://faithfulpack.net>, licence at
<https://faithfulpack.net/license>. Nothing derived from it is committed here or shipped: like the
vanilla art, it is built on your machine from your own copy, which is what their licence asks for.

## Licence

The code is **BSD 2-Clause** — see [LICENSE](LICENSE).

That covers what is in this repository. Two things it cannot cover:

**Minecraft's own terms apply on top.** Mojang allows mods and says they belong to you, as long as
you do not sell them or make money from them. A permissive licence cannot grant more than that, so
the usual permissive wording about commercial use is subordinate to
[Mojang's usage guidelines](https://www.minecraft.net/en-us/usage-guidelines).

**The derived textures are not ours to license.** They are edits of Mojang's art, and Mojang's
terms permit modifying the default textures for personal use but not redistributing them. That is
why the mod ships as source and generates them on your machine from the game you already own,
rather than shipping a jar with the edits baked in.
