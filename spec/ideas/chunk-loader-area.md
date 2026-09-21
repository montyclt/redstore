# Idea: a chunk loader that covers more than its own chunk

**Status:** proposed on 2026-09-21. Not committed to.
**Would belong to:** [../blocks/chunk-loader.md](../blocks/chunk-loader.md), as a change to a block
that is already built and working.
**Shared context:** [README.md](README.md).

## What it does

The loader stops carrying its pearl in the recipe and starts holding pearls as a setting. The more
pearls it holds, the larger the square of chunks it keeps loaded, and the pearls are visible
floating over the pedestal, so the size can be read off the block.

As first sketched: nought to four pearls, for nought (off), one, four (2 × 2), nine (3 × 3) or
sixteen (4 × 4) chunks.

## What it is worth

The block's own spec already lists the problem this solves, and so does the README: a contraption
that straddles a chunk border needs a loader on each side, and a machine of any size needs one per
chunk. That is the mod's usual argument turned on its own block — one cell instead of nine is
exactly what every other block here does for a contraption.

It also replaces the binary switch with something a builder can read from across the room. *On or
off* becomes *how big*, shown by the pearls themselves, which is a better read-out than the one the
block has today.

## The number that decides the price

**A thrown ender pearl and a `/forceload`ed chunk hold exactly the same shape.** Verified against
26.3 rather than assumed:

```java
// ServerPlayer.placeEnderPearlTicket
addTicketWithRadius(TicketType.ENDER_PEARL, chunkPos, 2)
// TicketStorage.addTicketWithRadius → new Ticket(type, ChunkLevel.byStatus(FULL) − radius)
//   = 33 − 2 = 31
// ChunkMap.FORCED_TICKET_LEVEL = ChunkLevel.byStatus(ENTITY_TICKING)
//   = 31
```

The radius of 2 is misleading: it is what puts the ticket level two steps below `FULL`, which lands
exactly on `ENTITY_TICKING`. So a pearl in flight gives **one** entity-ticking chunk, a 3 × 3 of
block ticking and a 5 × 5 loaded — the same as a forced chunk, to the level.

In survival, therefore, **one pearl buys one ticking chunk**, which is also what the block's recipe
charges today.

That makes `n` pearls → `n²` chunks a **fourfold multiplier at four pearls**: sixteen chunks for the
price of four stasis chambers. Design rules 1 and 2 in [../README.md](../README.md) rule that out —
the mod compresses what survival can already do and adds no new power. The honest exchange rate is
one pearl per chunk, with the pearl moving from the recipe to the fuel.

## The question that has to be answered first

**A square with an even side has no centre.** For 2 × 2 and 4 × 4, the loader sits in one chunk and
the rest of the square extends *somewhere* — and the player cannot see where. The block has no
facing, there is no mark on the ground, and without F3+G there is no way to know which chunks are
covered. Every anchoring rule available is both arbitrary and invisible.

Odd sides do not have the problem: 1, 3 × 3 and 5 × 5 are always centred on the loader and explain
themselves. Combined with the price above, that gives 1, 9 and 25 pearls. The jump from one to nine
is steep, and it is steep because that is what nine stasis chambers cost.

## What it would cost to build

* **The menu is probably avoidable, and should be.** Vanilla's idiom for *a block holds a few items
  and shows how many* is the chiseled bookshelf: six books, no GUI, right-click to put one in or
  take one out, and the model says how many are there. The lectern, the jukebox and the flower pot
  are the same shape. That avoids a second `AbstractContainerMenu`, a second screen, the
  synchronisation and the slot handling, and keeps the promise §4 of the block's spec makes today:
  no GUI and no status read-out.
* **The one-click switch goes.** [../blocks/chunk-loader.md](../blocks/chunk-loader.md) §1.2 argues
  for the switch on the grounds that a loader is the one block here you want to stop without
  breaking. If pearls are the control, switching off means taking them out and carrying them. That
  is a real loss and needs an answer — either accept it, or keep a separate toggle.
* **The block becomes a container.** Its contents have to drop when it breaks, which changes the
  loot table; and if it implements `Container`, a hopper can feed it, which means a machine could
  enlarge its own loaded area unattended.
* **The bookkeeping stops being one-to-one.** Today a loader claims one chunk. With areas it claims
  up to twenty-five, and two overlapping loaders go from a corner case to the normal case. The
  per-chunk count in `ChunkLoaderSavedData` already generalises, but this is the part of the block
  where a mistake leaves chunks loaded for ever.
* **Each block weighs more.** §3 says placement is never capped and a server that does not want
  loaders removes the recipe. That stays true, but one block covering twenty-five chunks changes
  what a single mistake costs.

## Why it is not in the mod today

Because the block was finished as one loader per chunk and works that way, and because the two
questions above — what a pearl buys, and where an even-sided square goes — are the design, not
details to settle during implementation. Parked until they are answered.
