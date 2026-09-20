# Redstore — Project Specification

> **Status:** design specification, no code written yet.
> **Codename:** `Redstore` (working name; the final public name is undecided — see [conventions.md](conventions.md)).
> **Target:** Minecraft **26.3**, Fabric, server + client.

## 1. What this mod is

Redstore is a small quality-of-life mod for **technical Minecraft servers**. It adds a handful of
redstone blocks that compress circuits which are already perfectly possible in vanilla into a single
block, so that contraptions stay readable and compact.

## 2. Design philosophy

1. **Nothing new, only smaller.** Every feature must be reproducible in vanilla Minecraft **in
   survival, by a player without commands**. A vanilla command is not a justification: `/forceload`
   needs cheats and an operator, so it cannot license a block that anyone can craft. The bar is
   what a survival player can already build. The mod removes tedium, not limits.
   * Logic gates → buildable with torches, repeaters and dust.
   * Redstone clock → buildable with a repeater loop and a lever.
   * Filter hopper → buildable with the classic comparator + hopper item sorter.
   * Chunk loader → an ender pearl stasis chamber, which since 1.21.2 keeps its chunk fully
     ticking for as long as the pearl stays in flight.
2. **No new power, no new exploits.** Timings, signal strengths and item throughput stay inside
   vanilla ranges. A gate is never faster than a vanilla equivalent (1 redstone tick), a filter
   hopper never moves items faster than a vanilla hopper (1 item / 8 game ticks).
3. **Predictable over clever.** Fixed delay, fixed orientation rules, no hidden state. A technical
   player must be able to look at a build and know exactly what it does.
4. **Borrow vanilla's rules verbatim.** When a block needs a rule — how a face reads a signal, how
   a filter compares items, how a delay behaves — copy the vanilla rule that already covers that
   situation instead of designing a better one. Someone who knows redstone must not have to learn
   anything new to predict this mod. A deviation is allowed only with a written justification in
   the block's spec (see [blocks/abstract-redstone-plate.md](blocks/abstract-redstone-plate.md) §4.1 for a
   worked example of the reasoning, which ended in copying vanilla).
5. **Server-friendly.** Everything the mod adds that can cost performance (only the chunk loader,
   really) is bounded by server-side configuration. See [config.md](config.md).
6. **Vanilla-shaped UX.** The blocks look, sound and are crafted like the vanilla redstone family.

## 3. Contents

| Block | ID | Summary |
| --- | --- | --- |
| AND gate | `redstore:and_gate` | Repeater-shaped plate, two side inputs, one front output. Right-click inverts it into NAND. |
| OR gate | `redstore:or_gate` | The same plate, OR / NOR. |
| XOR gate | `redstore:xor_gate` | The same plate, XOR / XNOR. |
| Filter hopper | `redstore:filter_hopper` | A hopper with one extra filter slot and whitelist/blacklist + strict-matching toggles. Only filters what *enters* the hopper. |
| Redstone clock | `redstore:redstone_clock` | A plate emitting a periodic signal. Right-click walks the eight settings: 1 to 4 redstone ticks per phase, as a square wave and as a 1-tick pulse. Runs by default; a redstone signal on either side stops it and shows the bedrock bar of a locked repeater. |
| Chunk loader | `redstore:chunk_loader` | Keeps its own chunk force-loaded and fully ticking, permanently and across server restarts. |

## 4. Spec layout

| File | Contents |
| --- | --- |
| [README.md](README.md) | This overview and index. |
| [conventions.md](conventions.md) | Toolchain, versions, package layout, naming, datagen, translations, creative tab. |
| [config.md](config.md) | Server configuration file and every knob it exposes. |
| [roadmap.md](roadmap.md) | Implementation phases and explicit non-goals. |
| [ideas/](ideas/) | Parked ideas: one file each, none of them committed to. |
| [blocks/abstract-redstone-plate.md](blocks/abstract-redstone-plate.md) | **Abstract.** What every flat plate component shares: form, orientation, signal reading rule, output, interaction grammar, model template, recipe principle. |
| [blocks/abstract-logic-gate.md](blocks/abstract-logic-gate.md) | **Abstract.** What the three gates share: two side inputs, evaluation contract, inversion, timing, states, models. |
| [blocks/and-gate.md](blocks/and-gate.md) | AND / NAND gate. |
| [blocks/or-gate.md](blocks/or-gate.md) | OR / NOR gate. |
| [blocks/xor-gate.md](blocks/xor-gate.md) | XOR / XNOR gate. |
| [blocks/redstone-clock.md](blocks/redstone-clock.md) | Redstone clock: period, modes, stop input, models, recipe. |
| [blocks/filter-hopper.md](blocks/filter-hopper.md) | Filter hopper: behaviour, menu, filtering rules, recipe, API notes. |
| [blocks/chunk-loader.md](blocks/chunk-loader.md) | Chunk loader: ticket management, persistence, limits, recipe, API notes. |

Each block file is self-contained: behaviour, block states, interactions, recipe, models, data files
and the concrete Fabric/Minecraft classes to implement it.

## 5. Licence and distribution

The code is BSD 2-Clause. The mod is distributed **as source, not as a jar**, because a built jar
contains textures derived from Mojang's own art and their terms do not allow redistributing those.
See [conventions.md](conventions.md) §10.1 for the reasoning and what it costs.

## 6. Conventions used in this spec

* **Must / should / may** are used in the RFC 2119 sense.
* `TODO(verify)` marks a statement that depends on a Minecraft 26.3 API detail that has to be
  confirmed against the decompiled sources before implementation. Each block file ends with an
  *API verification checklist* collecting them.
* All identifiers are written in full (`redstore:and_gate`) to avoid ambiguity.
