# Filter hopper

`redstore:filter_hopper`

A hopper that only accepts the items you tell it to. It replaces the vanilla
comparator + hopper + filler-item sorter cell with a single block, without changing throughput.

## 1. Summary of behaviour

* Behaves **exactly like a vanilla hopper** in every respect not mentioned below: same 5 storage
  slots, same facing rules, same 8-game-tick transfer cooldown, same 1 item per transfer, same
  `ENABLED` blockstate (a redstone signal locks it), same comparator output, same pushing/pulling
  targets, same item-entity suction from the block space above.
* One extra **filter slot** holds a real item stack (max 1 item).
* Two toggles: **whitelist / blacklist** and **loose / strict** matching.
* The filter governs **what enters the hopper only**. Anything already inside always leaves through
  the output as usual, so a filter change can never permanently trap items.

## 2. Filtering rules

Let `f` be the filter stack and `s` the incoming stack.

| Filter slot | Mode | Result |
| --- | --- | --- |
| empty | whitelist | accept everything (identical to a vanilla hopper) |
| empty | blacklist | accept everything |
| set | whitelist | accept only if `matches(f, s)` |
| set | blacklist | accept only if `!matches(f, s)` |

`matches(f, s)`:

* **Loose** (default): `f.is(s.getItem())` — item type only. A filter holding a plain iron sword
  accepts every iron sword, enchanted, renamed or damaged.
* **Strict**: `ItemStack.isSameItemSameComponents(f, s)` — the data components must match too
  (enchantments, custom name, dyed colour, potion contents, …). Damage is a component, so a strict
  filter holding an undamaged pickaxe rejects a damaged one.

### 2.1 Which operations the filter affects

The filter is implemented as a single override of `canPlaceItem(int slot, ItemStack stack)` on the
block entity, which vanilla consults on every insertion path. Therefore it applies to:

| Path | Filtered? |
| --- | --- |
| Item entities sucked in from the block space above | yes |
| Another hopper (or filter hopper) pushing into it | yes |
| A dropper/dispenser/minecart pushing into it | yes |
| The hopper pulling items out of the container above it | yes — it will not pull a disallowed item out of a chest |
| A player shift-clicking or dragging into the 5 storage slots | yes (deliberately consistent) |
| The filter slot itself | no — it accepts any item |
| Pushing items out to the target container | no — the filter never blocks the output |
| Hopper minecart running underneath and collecting from it | no (that is an extraction) |

## 3. Menu

### 3.1 Contents

| Index | Slot | Notes |
| --- | --- | --- |
| 0–4 | storage | the hopper's 5 slots, filter-checked via `Slot#mayPlace` |
| 5 | filter | max stack size **1**, accepts anything, never filter-checked |
| 6–32 | player inventory | |
| 33–41 | player hotbar | |

The filter stack is **not** part of the hopper `Container`: `getContainerSize()` returns 5 so that
none of the vanilla hopper transfer logic can ever touch the filter item. The filter lives in a
separate one-slot `SimpleContainer` owned by the block entity.

### 3.2 Layout

The three controls that *are* the filter — the item, the whitelist/blacklist toggle and the
strict/loose toggle — sit together as one group on the left, separated by a gap from the five
storage slots. An earlier layout put the toggles immediately after the storage row, which read as
if they belonged to it.

```
 ┌──────────────────────────────────────────────┐
 │ Filter Hopper                                │
 │  [item] [W/B] [=/~]     [ ][ ][ ][ ][ ]      │
 │  └──── the filter ────┘   five storage slots │
 │                                              │
 │  Inventory                                   │
 │  …                                           │
 └──────────────────────────────────────────────┘
```

| Element | x | y | size |
| --- | --- | --- | --- |
| Title `container.redstore.filter_hopper` | 8 | 6 | – |
| Filter slot | 8 | 20 | 18 × 18 |
| Whitelist/blacklist button | 26 | 19 | 18 × 18 |
| Strict/loose button | 44 | 19 | 18 × 18 |
| *(gap)* | 62–77 | | 15 px |
| Storage slots 0–4 | 78 + 18·i | 20 | 18 × 18 |
| Player inventory + hotbar | 8 | 51 | vanilla hopper positions |

**Built without any GUI art of its own.** The screen blits the vanilla hopper sheet
(`minecraft:textures/gui/container/hopper.png`, 176 × 133), then erases the five slot frames the
sheet paints at x 43–133 using a patch of blank panel copied from its own right-hand gutter at
(140, 19), and finally stamps six frames back at the positions above, copied from the sheet's own
slot art at (43, 19). The two toggles are plain vanilla `Button` widgets, which need no texture.

The buttons carry a one-letter label and a tooltip describing the **current** mode
(`gui.redstore.filter.mode.*`, `gui.redstore.filter.strict.*`), both refreshed every
`containerTick` from the synced data slots.

### 3.3 Synchronisation

* Button presses travel through `AbstractContainerMenu#clickMenuButton(Player, int id)`:
  `id = 0` toggles whitelist/blacklist, `id = 1` toggles strict matching. Both validate
  `stillValid(player)` before applying, then mark the block entity changed.
* The two booleans are mirrored to the client with a `ContainerData` of size 2 (`0/1` values) so the
  buttons render correctly for every viewer.
* The filter slot syncs like any normal slot.

## 4. Block state and block entity

Block state properties are inherited from the vanilla hopper and unchanged:

| Property | Values |
| --- | --- |
| `facing` | `down`, `north`, `south`, `west`, `east` |
| `enabled` | `true` / `false` (false while powered by redstone) |

Block entity persisted data (in addition to the 5-slot inventory, which is saved exactly like a
vanilla hopper's):

| NBT key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `Filter` | item stack (codec-encoded) | empty | the filter item |
| `Blacklist` | boolean | `false` | mode toggle |
| `Strict` | boolean | `false` | matching toggle |
| `TransferCooldown` | int | inherited from hopper logic |

Saved through `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`.

Breaking the block drops the 5 storage slots **and** the filter item.

Comparator output: unchanged vanilla behaviour over the 5 storage slots only — the filter item does
not contribute.

## 5. Recipe

Shapeless, yields 1:

```
hopper + item frame  →  filter hopper
```

The item frame is exactly what a vanilla player puts on a sorter cell to label it, so the recipe
reads as "a hopper that knows its label". Cost is deliberately low: the block it replaces (a sorter
cell) costs a comparator, a hopper, a torch, dust and 41 filler items, so the mod is still a saving
in space rather than in resources.

The reverse recipe is **not** provided; breaking the block returns the filter item anyway.

Unlock trigger: `has(Items.HOPPER)`, written as a recipe-unlock advancement in
`data/redstore/advancement/recipes/redstone/filter_hopper.json`. Without one the recipe never shows
up in the crafting table's book.

## 6. Appearance

A vanilla hopper with a **wooden item-frame trim around its mouth**. The metaphor is the point: an
item frame is what a vanilla player puts on a sorter cell to label it, and it is also this block's
second crafting ingredient, so the block wears the thing that makes it different.

How the textures are made: the `generateAssets` Gradle task builds all five derived assets from the
vanilla jar (see [../conventions.md](../conventions.md) §10). Every texture is the vanilla hopper
texture with the rim pixels recoloured
from grey to the item frame's wood palette, mapping each grey in the hopper's shading ramp to the
wood tone of matching lightness. That keeps vanilla's pixel-art shading exactly and changes only
the hue, so the block sits next to a normal hopper without looking like a different art style.

| File | Derived from | Recoloured region |
| --- | --- | --- |
| `assets/redstore/textures/block/filter_hopper_top.png` | `block/hopper_top` | the outer two-pixel ring |
| `assets/redstore/textures/block/filter_hopper_outside.png` | `block/hopper_outside` | rows 0–1, the top of the collar |
| `assets/redstore/textures/item/filter_hopper.png` | `item/hopper` | the top rim of the icon |

The ring on the top face and the band on the collar meet at the block's top edge, so the trim reads
as one continuous frame from any angle, and the dark funnel mouth stays grey inside it.

Grey → wood mapping, from the hopper ramp to the item frame ramp:

| Hopper grey | Item frame wood |
| --- | --- |
| `#676161` | `#AC5D31` |
| `#595858` | `#A45531` |
| `#4F4F4F` | `#944C29` |
| `#494848` | `#834829` |
| `#3F3E42` | `#7B4429` |
| `#343438` | `#734029` |
| `#2D2D32` | `#603623` |

Models: `redstore:block/filter_hopper` and `redstore:block/filter_hopper_side` are copies of the
vanilla hopper models with the texture references swapped; the inside face still points at
`minecraft:block/hopper_inside`, which has no rim to recolour. The item is a flat sprite, like the
vanilla hopper item, not the 3D model.

Optional polish (see [../roadmap.md](../roadmap.md)): a `BlockEntityRenderer` drawing the filter
item inside that frame, which is what the trim is visually promising.

### 6.1 The filter is shown on the sides

A block entity renderer draws the filter item on each side of the collar except the one the spout
points at. This is not decoration: the contraption this block replaces is a hopper with an item
frame on it, and half of why a vanilla sorter can be read at a glance is that every cell shows
what it takes. A block that hid its filter would be worse than the thing it compresses.

It costs two things, and both are bounded on purpose:

* **A packet.** The filter has to reach the client, so the block entity sends `getUpdateTag` /
  `getUpdatePacket` — carrying **only** the filter stack. The five storage slots never leave the
  server; a sorter is several hundred hoppers moving an item every eight ticks, and broadcasting
  their contents would be a packet storm for nothing. The update is sent with
  `Block.UPDATE_CLIENTS`, not `UPDATE_ALL`: a filter change is a picture, not a signal.
* **A draw.** An item model is not free either, so the renderer's view distance is **24 blocks**.
  A wall of sorter cells draws its labels when a player is close enough to read them and nothing
  at all from across the base.

Registration goes through vanilla's own `BlockEntityRenderers.register`, reachable because Fabric
API ships transitive access wideners for it; the Fabric helper that used to do this is deprecated.

## 7. Implementation notes

The awkward part is reusing vanilla hopper logic. `HopperBlockEntity`'s only constructor hardcodes
`BlockEntityType.HOPPER`, so it cannot be subclassed. What saves the situation is that most of its
algorithm is exposed as public statics taking `Container` and `Hopper` rather than the concrete
type; only `tryMoveItems`, `ejectItems`, `tryTakeInItemFromSlot` and `tryMoveInItem` are private.

**Chosen approach: call vanilla where it is public, port only what is not.** Most of
`HopperBlockEntity`'s algorithm is public static and takes interfaces rather than the concrete
type, so the mod calls it directly:

| Vanilla member | Used for |
| --- | --- |
| `HopperBlockEntity.suckInItems(Level, Hopper)` | the whole input side: pulling from the container above and picking up item entities |
| `HopperBlockEntity.addItem(Container, Container, ItemStack, Direction)` | insertion, including the `WorldlyContainer` face checks |
| `HopperBlockEntity.getContainerAt(Level, BlockPos)` | resolving chests, double chests and container entities |

Only `ejectItems` is private, so `HopperLogic` is just that one method plus two helpers — 98 lines
instead of the 269 of the original full port. Keeping the copied surface small is the point: every
ported line is a line that can silently drift from vanilla behaviour when Mojang changes something,
with nothing failing to compile.

Using `suckInItems` requires the block entity to implement `Hopper`, which is four trivial methods:
the block centre for `getLevelX/Y/Z` and `true` for `isGridAligned`. It also means the filter is
enforced by vanilla's own code path, since `suckInItems` routes every insertion through
`canPlaceItem`.

No ClassTweaker entry is needed: the mod uses no non-public vanilla member.

Known behavioural difference, recorded in the source: vanilla staggers a destination hopper by
setting its transfer cooldown when it was empty, but only recognises its own `HopperBlockEntity`.
Pushing into a filter hopper therefore does not stagger it, so a chain of filter hoppers can run
slightly out of phase with a chain of vanilla ones.

The block entity is therefore:

```java
public class FilterHopperBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider
```

with the five storage slots in a `NonNullList<ItemStack>` and the filter in a separate
`SimpleContainer(1)`, so `getContainerSize()` stays at 5 and no transfer path or comparator can
ever see the filter item.

The filter itself is one override:

```java
@Override
public boolean canPlaceItem(int slot, ItemStack stack) {
    return this.filterAccepts(stack);
}

@Override
public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
    return this.filterAccepts(stack);
}

@Override
public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
    return true; // extraction is never filtered
}
```

`FilterHopperBlock extends HopperBlock`, which is where the shape, the model, placement, `facing`,
`enabled` and the comparator output come from for free. It overrides `codec()` (with a cast, since
`HopperBlock` narrows the return type to the invariant `MapCodec<HopperBlock>`), `newBlockEntity`,
`getTicker`, `useWithoutItem` to open our menu, and the removal hook to drop the filter item
alongside the contents.

## 8. What 26.3 actually provides

Read off the decompiled jar, and recorded here because several of them contradict what the API
looked like one version earlier:

| Expectation in an earlier draft | What 26.3 actually does |
| --- | --- |
| Blocks carry a `MapCodec` and override `codec()` | **Gone.** Neither `Block` nor `HopperBlock` has a codec member; a block subclass declares none. |
| `SimpleContainer#addListener` | Does not exist. Use an anonymous subclass overriding `setChanged()`. |
| `AbstractContainerScreen` sets `imageWidth`/`imageHeight` in the body | Both are `final`; they are passed to the 5-argument constructor. |
| The block's removal hook must drop the container's contents | **It must not.** `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` already calls `Containers.dropContents` for any block entity that is a `Container`. Doing it again in the block duplicates every item in the hopper — a dupe bug. The filter, living in its own container, *is* ours to drop, by overriding that same hook and calling `super` first. |
| Access widener | Replaced by ClassTweaker, and the mod declares none. The one vanilla member it reaches that is not public, `BlockEntityRenderers.register`, is opened by Fabric API's own transitive access wideners. |

Confirmed as written: `Identifier`, `BlockItemId.create`, the two-overload block registration with
`setId`, `FabricBlockEntityTypeBuilder`, `ValueOutput#store(String, Codec, T)` /
`ValueInput#read(String, Codec)` / `putBoolean` / `getBooleanOr` / `getIntOr`,
`MenuType.MenuSupplier` registration, `AbstractContainerMenu#SLOT_SIZE`,
`#addStandardInventorySlots`, `#checkContainerSize`, `#checkContainerDataCount`, `#addDataSlots`,
`#clickMenuButton`, `ContainerHelper#saveAllItems`/`loadAllItems`/`removeItem`/`takeItem`,
`Container#stillValidBlockEntity`, `EntitySelector.CONTAINER_ENTITY_SELECTOR` and
`ENTITY_STILL_ALIVE`, `BaseEntityBlock#createTickerHelper`,
`HopperBlock#affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)`,
`MenuScreens.register`, and the screen's `extractBackground(GuiGraphicsExtractor, …)` with
`RenderPipelines.GUI_TEXTURED`.
