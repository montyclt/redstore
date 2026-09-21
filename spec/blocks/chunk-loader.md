# Chunk loader

`redstore:chunk_loader`

A full block that keeps the chunk it stands in permanently force-loaded and fully ticking, with no
fuel, no redstone control and nothing to configure.

**Its vanilla equivalent is an ender pearl stasis chamber**, not `/forceload`. Since 1.21.2 a
thrown ender pearl keeps the chunk it occupies loaded and fully ticking, and a stasis chamber — a
soul sand bubble column under water — holds a pearl in flight indefinitely, so a survival player
can already pin a chunk open with eight buckets of water, a soul sand block and one pearl. This
block is that contraption compacted into a cell, with the pearl spent at the crafting table
instead of held in a column of water.

The distinction matters because of design rule 1: a command is **not** a justification. `/forceload`
needs cheats and an operator, so it could never license a block that any player can craft. What
licenses this block is that the same result is already buildable in survival.

## 1. Behaviour

* While the block exists, its own chunk (1 × 1) is force-loaded at **ticket level 31** — the same
  level a player produces. Block entities tick, redstone runs, random ticks fire, entities inside
  the chunk tick, hoppers move items, furnaces smelt, crops grow.
* The load **persists across server restarts**. The chunk is loaded again as soon as the dimension
  is loaded, without anyone having to visit it.
* The load ends only when the block is broken, including by an explosion.
* No redstone control, no fuel, no owner-online requirement. Placed means loaded.
* Exactly its own chunk. To cover a larger area, place more loaders — this keeps the cost of a
  build visible and linear.

### 1.1 Documented limitations

These are vanilla behaviours, not bugs, and must be stated in the mod description:

* **A force-loaded chunk is not a player.** Everything vanilla gates on *player proximity* stays
  gated — natural mob spawning and monster spawners do not run. Everything driven by *entity AI*,
  *block entities* or *random ticks* does run. Section 10 works through exactly which farms that
  leaves working and which it does not; the short version is that an iron farm works and a dark
  room does not.
* Weather, time and random ticks behave exactly as in a chunk a player is standing in.
* The block does not extend the *entity ticking* border beyond its own chunk, so contraptions that
  straddle a chunk border need a loader on each side.

## 2. Ticket management

### 2.1 Acquiring and releasing

* Acquire: `serverLevel.setChunkForced(chunkX, chunkZ, true)`.
* Release: `serverLevel.setChunkForced(chunkX, chunkZ, false)`, **only** when no other Redstore
  chunk loader remains in that chunk and the force-load was registered by us.
* Vanilla persists forced chunks in the dimension's saved data, which is what gives us free
  restart persistence.

### 2.2 Ownership bookkeeping

Forced chunks are a single shared vanilla set: `/forceload` and other mods write to it too.
Releasing blindly would silently undo an operator's `/forceload`. The mod therefore keeps its own
`SavedData` per dimension, `redstore_chunk_loaders`:

| Field | Type | Meaning |
| --- | --- | --- |
| `loaders` | map `ChunkPos → list of BlockPos` | every chunk loader block we know about |
| `preexisting` | set of `ChunkPos` | chunks that were already force-loaded by someone else when our first loader claimed them |

Release rule: unforce a chunk only if, after removing this block, `loaders` has no entry left for
that chunk **and** the chunk is not in `preexisting`.

### 2.3 Deferring the call

`ServerLevel#setChunkForced` synchronously loads the chunk. Calling it from inside a chunk-load
callback re-enters the chunk system and is known to deadlock with C2ME. Therefore **every**
acquire/release is scheduled onto the server thread for the next tick via `server.execute(...)`
instead of being run inline from a block-entity `onLoad` or from a chunk event.

### 2.4 Startup reconciliation

On `ServerLifecycleEvents.SERVER_STARTED`, and then once per dimension when it first loads, the
manager:

1. Reads the saved data for the dimension.
2. Re-issues `setChunkForced(..., true)` for every recorded chunk (cheap and idempotent; it also
   repairs the case where the vanilla forced-chunk set was edited or lost).
3. Once each recorded chunk is loaded, verifies that a `ChunkLoaderBlockEntity` is actually present
   at each recorded `BlockPos`. Stale entries (world edited externally, block removed while the mod
   was uninstalled) are dropped and the chunk is released.

## 3. Placement

Placement never fails. Any player may place a loader anywhere, in any dimension, as many times as
they can pay for the recipe — exactly as any player may already keep as many stasis chambers in
flight as they care to build. A per-player or per-server cap would be a rule vanilla does not have,
and this mod does not invent rules ([../README.md](../README.md), design rule 4).

A server that does not want chunk loaders removes the recipe with a datapack, the way it would
remove a vanilla one. That is a decision about what can be crafted, which is where Minecraft
already puts it, and it needs nothing from the mod.

Nothing records who placed a loader: with no limit to enforce, an owner would be a UUID kept in
saved data for no one to read.

## 4. Interaction and feedback

| Action | Effect |
| --- | --- |
| Right-click with an empty hand | Action-bar status: `message.redstore.chunk_loader.status` — the chunk's coordinates and dimension. No GUI. |
| Break | Releases the chunk (subject to §2.2) and drops the block item. |

Visual state: the block always renders "active" — there is no off state. It emits light level 7 and
spawns two `ParticleTypes.PORTAL`-style particles per second on the client above the block so that
active loaders are findable in a dark base.

## 5. Block properties

```java
BlockBehaviour.Properties.of()
    .strength(3.0F)
    .requiresCorrectToolForDrops()
    .sound(SoundType.METAL)
    .lightLevel(state -> 7)
    .pushReaction(PushReaction.BLOCK)   // pistons must not move it
```

* No block state properties; all data lives in the block entity.
* `PushReaction.BLOCK` is deliberate: a moving chunk loader would mean tickets churning every
  redstone tick.
* Not immune to explosions — it drops normally.

## 6. Block entity

| NBT key | Type | Meaning |
| --- | --- | --- |
| `Claimed` | boolean | whether this block currently holds the chunk claim (used to detect a half-applied state after a crash) |

It does **not** tick. Its only job is to register itself with `ChunkLoaderManager` on
`onLoad`/`setPlacedBy` and to be found again by the reconciliation pass.

Removal must distinguish a real break from a chunk/world unload:

* Release on the block's removal hook (`onRemove` / `affectNeighborsAfterRemoval` — `TODO(verify)`
  which one is current in 26.3), i.e. when the block state actually changes to something else.
* **Never** release from `BlockEntity#setRemoved`, which also fires on world shutdown.

## 7. Recipe

Shaped, yields 1. Legend: `I` iron ingot · `P` ender pearl · `E` eye of ender.

```
I  P  I
P  E  P
I  P  I
```

4 iron ingots, 4 ender pearls, 1 eye of ender — mid-game (needs a blaze rod and pearls), thematic
(ender = "this place stays real even when you are not looking at it"), and symmetric so it is easy
to remember. Servers that consider it too cheap can disable the block entirely or cap it; the
recipe itself is fixed.

Unlock trigger: `has(Items.ENDER_EYE)`.

## 8. Appearance

* Full cube. Sides: a dark polished-stone/iron frame with an inset eye-like core; top and bottom
  carry the same frame with a small lens in the centre.
* Textures: `chunk_loader_side.png`, `chunk_loader_top.png` (16 × 16 each; the bottom reuses the
  top).
* Item model: the block model.
* No animation in phase 1; an emissive overlay on the core is optional polish.

## 9. Loot and data files

* Loot table: drops itself (requires a stone-tier pickaxe, per `minecraft:needs_stone_tool`).
* Tags: `minecraft:mineable/pickaxe`, `minecraft:needs_stone_tool`.
* **A recipe-unlock advancement**, which is not optional: a recipe only appears in the crafting
  table's book once something unlocks it, and without one the block is craftable but invisible to
  anyone who does not already know the shape. It lives in
  `data/redstore/advancement/recipes/redstone/<name>.json` and follows vanilla's own pattern —
  `inventory_changed` on the ingredient that says what the block is for, plus `recipe_unlocked`,
  granting the recipe.
* No achievement-style advancements.
* Creative tab: **Functional Blocks**, not Redstone Blocks — see
  [../conventions.md](../conventions.md) §6.2. It is the only block in the mod that is not a
  redstone component.

## 10. Notes and considerations — what runs unattended

This section exists because "the chunk is loaded" is not the same question as "does my farm run".
It is reference material for the mod description and for the server's own documentation. Every
claim in it is read off vanilla's own code, cited where it matters, since the spawning and ticket
paths have moved repeatedly across the 1.21.x → 26.x line.

### 10.1 Two different systems create mobs

**A. The natural spawning cycle (`NaturalSpawner`).** Runs once per tick over the entity-ticking
chunks, but every candidate position asks for the nearest player first:

```java
Player player = level.getNearestPlayer(x, y, z, -1.0, false);
if (player != null) {
    double d = player.distanceToSqr(x, y, z);
    if (isRightDistanceToPlayerAndSpawnPoint(...)) { /* spawn */ }
}
```

With no player in the dimension, `player == null` and nothing spawns. The mob cap reinforces this:
it is computed as `cap × chunksNearPlayers / 289`, which is zero with nobody online in that
dimension. This system is player-dependent **by design**.

**B. Entity AI.** A mob runs its brain/goals as part of its own tick, and an entity ticks if and
only if its chunk is at ticket level **31 (ENTITY_TICKING)**. Nothing in that path consults a
player.

Iron golem spawning belongs to system B, not A: it is the `SpawnGolem` behaviour in the villager's
core activity, i.e. ordinary code running while the villager ticks. The golem is created directly
as `MOB_SUMMONED`, which bypasses spawn rules, light checks and the mob cap.

### 10.2 Why iron farms work

`ServerLevel#setChunkForced` installs a level-31 ticket — exactly what a player standing in the
chunk produces. So the villagers tick fully: they gossip, follow their schedule, sleep, work,
panic, and roll the golem spawn attempt (1/700 per tick once the conditions hold). The village-level
conditions are evaluated normally, because they depend on world time and on the villagers' own
memories, both of which keep advancing: 75 % of the (non-nitwit) villagers must have reached their
workstation within the last 24000 ticks, 100 % must be linked to a bed, and the villager must not
hold `GOLEM_DETECTED_RECENTLY`.

The rest of the chain is covered too: the golem falls into the kill chamber (entity physics is part
of the entity tick), the drops appear, and the hoppers move the iron (block entities tick).

### 10.3 Farms that work, and farms that do not

| Farm type | Runs on a chunk loader alone? | Why |
| --- | --- | --- |
| Iron farm (villager golem spawning) | **Yes** | Entity AI (§10.1 B). |
| Villager breeders, animal breeders | Yes | Entity AI + block entities. |
| Crop, bamboo, sugar cane, kelp, dripstone, ice | Yes | Random ticks. |
| Smelters, item sorters, storage systems, redstone clocks | Yes | Block entities and scheduled ticks. |
| Mob grinders on existing mobs (guardian-less, trapped mobs) | Yes | Entity AI. |
| Nether portal zombified piglin farms | Probably — random tick on the portal block | No player check in the portal's random tick, but the spawn path is not traced here. |
| Dark room / general hostile mob farms | **No** | Natural spawning (§10.1 A). |
| Monster spawner (dungeon, trial) farms | **No** | `BaseSpawner` requires a player within 16 blocks. |
| Guardian / raid / wandering trader farms | **No** | All player-gated. |

A nasty detail worth writing in the server rules: if **some** player is online in that dimension but
far away, natural spawning *does* get attempted in the force-loaded chunk, and the mobs then
despawn immediately for being more than 128 blocks from the nearest player. The farm produces
nothing while still consuming mob cap and CPU. A dark room on a chunk loader is not merely useless,
it is actively harmful to the server.

### 10.4 Practical caveats for builders

1. **Only the loader's own chunk has entity ticking.** Neighbours get level 32+ by propagation:
   blocks tick, entities do not. The golem spawn search covers a box of roughly ±8 blocks around
   the villager, so an attempt can land in the next chunk over — and a golem created in a chunk
   without entity ticking simply freezes in place, never falling into the kill chamber. Keep the
   villagers and the spawn platform well inside one chunk, or place loaders in the adjacent chunks.
2. **Name-tag the zombie** in panic-based designs. Despawning is measured against the nearest
   player, so an unnamed zombie is removed as soon as anyone is online in that dimension more than
   128 blocks away. (With nobody in the dimension, `getNearestPlayer` returns null and nothing
   despawns.)
3. Villagers never despawn, so that half of the farm is safe regardless.
4. A contraption crossing a chunk border needs a loader per chunk; `/forceload query` (or
   `/redstore loaders list`) is the way to check.

## 11. API verification checklist (26.3)

- [ ] `ServerLevel#setChunkForced(int, int, boolean)` still exists and still writes to persistent
      saved data.
- [ ] Whether 26.3 exposes a first-class chunk-ticket API that would be preferable
      (`ServerChunkCache#addRegionTicket` with a custom `TicketType`), and whether such tickets
      persist across restarts — if they do not, `setChunkForced` remains the right call.
- [ ] `SavedData` registration API (`SavedDataType` / codec-based factory in recent versions).
- [ ] The current block removal hook name (`onRemove` vs `affectNeighborsAfterRemoval`).
- [ ] `ServerLifecycleEvents` / `ServerWorldEvents` entry points in Fabric API 0.161.0+26.3.
- [ ] `NaturalSpawner` / `BaseSpawner` player checks, to back the claims in section 10.
