# Chunk loader

`redstore:chunk_loader`

A block that keeps the chunk it stands in permanently force-loaded and fully ticking, with no
fuel, no redstone control and nothing to configure. A right-click switches it off and on.

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

* While the block exists, its own chunk (1 × 1) is force-loaded at the **entity-ticking level** —
  the same level a player produces, and by the same mechanism: vanilla's own `FORCED` ticket. Block entities tick, redstone runs, random ticks fire, entities inside
  the chunk tick, hoppers move items, furnaces smelt, crops grow.
* The load **persists across server restarts**. The chunk is loaded again as soon as the dimension
  is loaded, without anyone having to visit it.
* The load ends when the block is broken, including by an explosion, or when a player switches
  it off.
* **A right-click toggles it.** Switched off, the block releases its chunk and keeps everything
  else: it is still there, still crafted, still remembers nothing it needs to. Switched on again,
  it takes the chunk back.
* No redstone control, no fuel, no owner-online requirement. Placed and switched on means loaded.

### 1.1 Documented limitations

These are vanilla behaviours, not bugs, and have to be stated where somebody will actually read
them, which is two places: the **block's own tooltip**, for the one that catches people out, and
the **README**, in full.

* **A force-loaded chunk is not a player.** Everything vanilla gates on *player proximity* stays
  gated — natural mob spawning and monster spawners do not run. Everything driven by *entity AI*,
  *block entities* or *random ticks* does run. Section 10 works through exactly which farms that
  leaves working and which it does not; the short version is that an iron farm works and a dark
  room does not.
* Weather, time and random ticks behave exactly as in a chunk a player is standing in.
* The block does not extend the *entity ticking* border beyond its own chunk, so contraptions that
  straddle a chunk border need a loader on each side.

### 1.2 Why a switch, when nothing else here has one

A loader is the one block in the mod whose cost is paid by the server rather than by the builder,
and the only way to stop paying it used to be to break the block — which loses the crafting and,
for a player who does not remember where the loaders are, loses the ability to find them at all.

The switch is safe to have because **it cannot be worked by a circuit**. Redstone control would
make a chunk loader something a machine turns on, which is a different block with a different cost;
a hand is the only thing that moves this one. And it is safe to *find*, because the pearl says
which way it is set from across the room (section 8).
* Exactly its own chunk. To cover a larger area, place more loaders — this keeps the cost of a
  build visible and linear.

## 2. Ticket management

### 2.1 Acquiring and releasing

* Acquire: `serverLevel.setChunkForced(chunkX, chunkZ, true)`, whose return value says whether
  the ticket was ours to add.
* Release: `serverLevel.setChunkForced(chunkX, chunkZ, false)`, **only** when no other Redstore
  chunk loader remains in that chunk and the force-load was registered by us.
* A loader that is switched off is not a loader: it does not appear in the records at all, so
  switching one off is a release and switching it on is an acquire.
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

`preexisting` fills itself on the way in: `setChunkForced(..., true)` returns whether the ticket
was added, so a `false` means somebody else had already forced that chunk — see section 11.

### 2.3 Deferring the call

`ServerLevel#setChunkForced` synchronously loads the chunk. Calling it from inside a chunk-load
callback re-enters the chunk system and is known to deadlock with C2ME. Therefore **every**
acquire/release is scheduled onto the server thread for the next tick via `server.execute(...)`
instead of being run inline from a block-entity `onLoad` or from a chunk event.

**The same rule covers reading, and not only writing.** The chunk-load callback runs *inside* the
chunk source, on a chunk it is still finishing, so everything it reads has to come off the chunk it
was handed — `chunk.getBlockState(pos)` — and never off the level. `Level#getBlockState` sends the
question back through the chunk source, which blocks the server thread until the chunk is full;
the chunk becomes full only when this callback returns, and the server hangs at `Preparing spawn
area` with no error of any kind.

Reconciliation (§2.4) is the exception, and deliberately: it runs from `SERVER_STARTED`, outside
the chunk system, where asking the level for a block state is a plain blocking chunk load and is
what is wanted.

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
| Right-click with an empty hand | Switches the loader off or on, and says which on the action bar. |
| Break | Releases the chunk (subject to §2.2) and drops the block item. |

There is no GUI and no status read-out: what a status message would have said — whether this chunk
is loaded — is what the pearl says by being there or not.

Light level 7 while switched on, and nothing while off, so a working loader is findable in a dark
base and a switched-off one is visibly not working.

## 5. Block properties

```java
BlockBehaviour.Properties.of()
    .strength(3.0F)
    .requiresCorrectToolForDrops()
    .sound(SoundType.AMETHYST)
    .lightLevel(state -> state.getValue(BlockStateProperties.ENABLED) ? 7 : 0)
    .pushReaction(PushReaction.IMMOVEABLE)   // pistons must not move it
    .noOcclusion()                      // it is twelve pixels tall, not a cube
```

* One block state property, vanilla's own `enabled`, and that is the whole of the block's state.
  What it has claimed lives in the per-dimension saved data (§2.2); the block entity stores nothing
  at all (§6).
* The shape is the **enchanting table's**: `[0, 0, 0]` to `[16, 12, 16]`, twelve pixels tall.
* `PushReaction.IMMOVEABLE` is deliberate: a moving chunk loader would mean tickets churning every
  redstone tick. The constant is named `IMMOVEABLE` in 26.3, misspelling and all — there is no
  `BLOCK`.
* Not immune to explosions — it drops normally.

## 6. Block entity

**It stores nothing and it does not tick.** What a loader has claimed is kept once per dimension,
in the saved data of section 2.2, because that answer has to outlive the block's chunk being
unloaded — which is precisely what a block entity does not do.

What it is for is being *found*. A chunk keeps a map of its block entities, so when a chunk loads
the manager can ask it for loaders in one lookup rather than walking a hundred thousand block
states. That is the repair path: a loader the record has never heard of — a world restored from a
backup, or one where the mod was uninstalled for a while — claims its chunk again the first time
anybody visits it.

A loader that is *switched off* claims nothing, so the hook checks `enabled` before asking.

It has a second job on the client, which came later and costs it nothing: a block entity is what a
block entity renderer hangs on, and the pearl is drawn by one (§8.2). That needs no field either —
the renderer reads `enabled` off the block state and the bob off the world clock.

Removal must distinguish a real break from a chunk/world unload:

* Release on the block's removal hook, `affectNeighborsAfterRemoval` (section 11), i.e. when the
  block state actually changes to something else.
* **Never** release from `BlockEntity#setRemoved`, which also fires on world shutdown.

## 7. Recipe

Shaped, yields 1. Legend: `P` ender pearl · `A` amethyst shard · `O` obsidian.

```
.  P  .
A  O  A
O  O  O
```

**It is the enchanting table's recipe, part for part.** Vanilla's is `" B "`, `"D#D"`, `"###"` —
book, diamonds, obsidian. This one puts a pearl where the book goes and amethyst where the
diamonds go, which is exactly what the block puts where the table puts them (section 8).

Read as a parts list: the pearl because a pearl is what holds a chunk open in vanilla, the amethyst
because the block wears it, the obsidian because the block is made of it.

Unlock trigger: `has(Items.ENDER_PEARL)`.

It is cheaper than the four-iron, four-pearl version an earlier draft asked for, and that is a
decision rather than an oversight: the contraption it replaces — a stasis chamber — costs eight
buckets of water, a block of soul sand and one pearl, so one pearl and some obsidian is the same
order of price. With the configuration gone (see [../conventions.md](../conventions.md) §6), a
server that wants them scarcer removes the recipe with a data pack; there is no cap to turn.

## 8. Appearance

**An enchanting table with a pearl instead of a book.** That is the whole design, and it is
borrowed rather than invented: vanilla already has a block whose vocabulary is *an object held in
the air above a pedestal*, and nobody has to be told what it means.

* The **pedestal** is the enchanting table, taken whole: its shape — twelve pixels tall — and its
  obsidian.
* The **corners** are the table's own gems, in the table's size and shape, recoloured from diamond
  to **amethyst**.
* The **cloth** is the table's own cloth, dyed from red to the **teal of the ender pearl**: the
  cloth is the colour of what it holds. It is also the colour the block had spare — the table was
  cold gems on warm cloth, and moving the gems to amethyst left the cold with nowhere to go.
* The **pearl** floats where the table's book floats, bobs the way the book bobs, and **faces the
  camera**. It is there when the loader is on and gone when it is off, which is the entire state
  read-out this block needs.

### 8.1 How the pedestal is derived

Everything in the pedestal comes out of vanilla's own art, like every other texture in the mod
([../conventions.md](../conventions.md) §10):

* `enchanting_table_top` and `enchanting_table_side` are copied with **two substitutions**, both
  colour for colour:
  * the five pale teals of the diamond corners become five tones of `block/amethyst_block`,
    lightest for lightest;
  * the reds of the cloth become the teal of `item/ender_pearl` **at their own brightnesses** —
    only the hue moves, so the folds and the shadow under the pearl are still the ones Mojang
    drew. It is the same trade the gates' inlay makes with the comparator's quartz, and the task
    shares the code for it.

  Nothing else in either file is touched: the obsidian is untouched obsidian.

  The cloth is **eight** tones and not five, because Faithful adds three of its own — see
  [../conventions.md](../conventions.md) §10.3. A tone the list does not name stays red, and a
  cloth half-dyed is worse than one not dyed at all.
* The model is `block/enchanting_table.json`, with those two textures swapped in and
  `enchanting_table_bottom` referenced unchanged. It is the pedestal and nothing else.

### 8.2 The pearl is drawn, not built

**It is the ender pearl item, turned to face the viewer.** That is not a trick invented here: it is
how vanilla draws a pearl that is in the air. A thrown pearl is never seen edge-on — and a flat
sprite that always faces you is rounder than any ball that can be built out of boxes. A pearl held
in stasis is a thrown pearl stopped mid-flight, so it is drawn the way a thrown pearl is drawn.

**It aims at the camera's position, not at its orientation**, and that is the one place this parts
company with `ThrownItemRenderer` (and with the conduit's wind, which is the same code). Turning the
pose by the camera's orientation aligns a sprite with the *screen*, which is not the same as
pointing it at the *viewer*: anything away from the middle of the screen is then seen at an angle
and shows its edge, and walking sideways without turning does not move the sprite at all. Both of
those are visible on a block you stand next to.

Vanilla can afford the cheaper one because the things it billboards are small, fast and usually in
front of you. So this takes the vector from the pearl to the camera and turns by its yaw and its
pitch — `atan2(dx, dz)` about Y, then `−atan2(dy, √(dx²+dz²))` about X, in that order — which puts
the sprite exactly perpendicular to the line of sight from wherever it is looked at.

**The float is the book's, to the number**, out of `EnchantTableRenderer`: three quarters of the way
up the block, then a tenth more, then `sin(time × 0.1) × 0.01` — a bob of a sixth of a pixel with a
period of about three seconds. The pedestal is the enchanting table's height, so the pearl ends up
exactly where the book floats. The book counts its own ticks because its block entity has somewhere
to keep them; ours keeps nothing (§6), so the world's clock counts and the block's position sets the
phase, which is what stops a row of loaders bobbing in lockstep.

This costs three things, and they are **the same three Mojang pays for the book**:

* A **block entity renderer** runs per frame for every visible loader. It is bounded by
  `getViewDistance()`, as the filter hopper's is.
* Past that distance the pearl is not drawn, so a working loader looks empty from far away.
* **The block item has no pearl**, because a block entity renderer does not draw the item model.
  The enchanting table's item has no book for exactly this reason — its item definition is a plain
  `minecraft:model` pointing at `block/enchanting_table`, and the table is not among the special
  model renderers.

#### What was tried first, and why it was wrong

The pearl was originally **built**, as boxes in the block model, so that it would be part of the
baked chunk mesh and cost nothing per frame. Three shapes were built and all three failed, and the
reason is worth keeping because it is a general one.

* **Stacked slabs** — wide in the middle, narrow at the ends — are a circle from the front and a
  square from above. They read as a spool.
* **A ring built as a cross**, a band with a lip in front and behind, only cuts the corners it can
  reach: nick them and it is still a square, cut them properly and it is plainly a cross.
* **Two squares at 45°**, which is the trick that suggests itself next, give an eight-pointed star.
  An octagon is where two squares *overlap*, and boxes in a model can only be added together, never
  intersected.
* A **voxelised sphere**, measured rather than designed and merged greedily into 26 disjoint boxes,
  finally did look round — at the price of 26 boxes, a generated texture and a second model.

So the shape was reachable, and it was still the wrong answer: twenty-six boxes to approximate what
one camera-facing sprite gives exactly, in a block whose own source — the enchanting table — had
already solved it the other way. Design rule 4 says to copy vanilla's answer rather than invent a
better one, and that applies to how a thing is drawn as much as to how it behaves.

## 9. Loot and data files

* Loot table: drops itself.
* Tags: `minecraft:mineable/pickaxe` and `minecraft:needs_diamond_tool` — obsidian's own tier,
  which the block is mostly made of, and which anyone who crafted one has already reached.
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

## 11. What 26.3 actually provides

Read off the decompiled jar. Everything this block was specified against is there, and two details
are better than the spec assumed.

**`ServerLevel#setChunkForced(int, int, boolean)`** exists and is the right call. It goes to
`ServerChunkCache#updateChunkForced` and on to `TicketStorage#updateChunkForced`, which adds a
ticket of type `TicketType.FORCED` at `ChunkMap.FORCED_TICKET_LEVEL`.

**Forced chunks persist because tickets do.** `TicketStorage` is itself a `SavedData`, with its own
`CODEC` and `SavedDataType`, and `TicketType.FORCED` carries `FLAG_PERSIST` — its flags are
`PERSIST | LOADING | SIMULATION | KEEP_DIMENSION_ACTIVE`, with no timeout. Restart persistence is
vanilla's, and free.

**The ticket level is the entity-ticking one, by construction.**
`ChunkMap.FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING)`, which is why
section 1 can promise entity ticking rather than hope for it.

**A ticket type of our own is not reachable.** `TicketType`'s constructor is public but its
`register` is private and persistence is by registered name, so a custom persistent type would need
a mixin. `setChunkForced` stays, and with it the shared-set problem section 2.2 exists to solve.

**`setChunkForced` answers the `preexisting` question by itself.** It returns whether the ticket was
actually *added*, so a `false` on acquiring means the chunk was already forced by somebody else —
which is exactly what section 2.2 keeps a set for, obtained without reading vanilla's set at all.

**`SavedData` is codec-based now.** A `SavedDataType(Identifier, Supplier<T>, Codec<T>, DataFixTypes)`
handed to `serverLevel.getDataStorage().computeIfAbsent(type)`. The storage class is
`SavedDataStorage`, not `DimensionDataStorage`.

**The removal hook is `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)`.**
`onRemove` is gone from `BlockBehaviour` entirely, so section 6's open question resolves to the one
it already guessed at.

**Fabric's events are there**, with one rename: `ServerLifecycleEvents.SERVER_STARTED` as written,
but the per-world one is **`ServerLevelEvents`** (`Load` / `Unload`), not `ServerWorldEvents`.

**Section 10's spawning claims hold.** `NaturalSpawner` calls `getNearestPlayer` and gates on
`isRightDistanceToPlayerAndSpawnPoint`; `BaseSpawner` keeps a `requiredPlayerRange` and an
`isNearPlayer` check. Neither can fire with nobody in the dimension.

One more datum, for section 1's argument rather than for the code: **the ender pearl's own ticket**
is `TicketType.ENDER_PEARL`, flags `LOADING | SIMULATION | KEEP_DIMENSION_ACTIVE` with a 40-tick
timeout. A pearl in flight simulates its chunk, which is precisely why a stasis chamber is this
block's vanilla equivalent — and the one flag it lacks is the one this block adds: `PERSIST`.
