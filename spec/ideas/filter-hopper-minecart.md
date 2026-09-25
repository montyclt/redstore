# Idea: filter hopper minecart

**Status:** proposed on 2026-09-22. Not committed to.
**Would belong to:** [../blocks/filter-hopper.md](../blocks/filter-hopper.md), as an entity beside
the block it is made from.
**Shared context:** [README.md](README.md).

## What it does

A hopper minecart that only picks up what its filter allows. The same filter slot, the same
whitelist or blacklist, the same strict or loose matching as the block, on a cart that collects
from the container above it and from item entities, exactly as a hopper minecart does.

## Its vanilla equivalent, and what it costs there

There is none for the cart itself. A vanilla hopper minecart takes **everything**, and a filter in
vanilla lives at the other end of the line: the cart is emptied into a hopper and the sorting
happens after. What this compresses is *a hopper minecart plus a sorter at the unloading station*
— and what it changes is where the choice is made.

## The question that has to be answered first

**Is a selective cart new power?** Vanilla has no way to make a minecart take only one item out of
a chest.

But the block already does exactly that. [../blocks/filter-hopper.md](../blocks/filter-hopper.md)
§2.1 says the filter applies to *the hopper pulling items out of the container above it* — it will
not pull a disallowed item out of a chest — and the vanilla sorter cell cannot do that either, since
it filters what arrives along a hopper line rather than what is taken from a container. That was
accepted for the block. The cart adds **mobility** to it, not a new kind of power; so the honest
position is that if §2.1 is right for the block the cart inherits the argument, and if it is wrong
then both are.

## What it would cost

**Less than it looks, because the vanilla cart is built to be extended** — the opposite of the
hopper block entity. Verified against 26.3:

* `MinecartHopper` takes its `EntityType` as a constructor parameter. `HopperBlockEntity` hardcodes
  `BlockEntityType.HOPPER`, which is why the block could not subclass it and why `HopperLogic`
  exists. The cart has no such wall.
* `canPlaceItem` is declared nowhere in its hierarchy, so it is `Container`'s default and a subclass
  can override it.
* Its `suckInItems()` is `HopperBlockEntity.suckInItems(level, this)` — **the same insertion path
  the block already uses**, which routes every item through `canPlaceItem`. The filter would apply
  the same way, by the same override.
* It **never pushes items out**. A hopper minecart only collects; it is emptied by a hopper under the
  rail. So there is nothing like `HopperLogic` to port for it at all.

So a subclass could override `canPlaceItem`, the display block, the drop item, the pick result and
the menu, and inherit everything else: the collecting, the five slots, the saving, and the activator
rail switching it off (`activateMinecart` → `setEnabled`). That is the `DiodeBlock` story again.

What would genuinely be new:

* **The menu has to generalise.** `FilterHopperMenu#clickMenuButton` assumes its storage is a
  `FilterHopperBlockEntity`. A cart would need it to mean *something with a filter and two toggles*.
* **A new entity type, item, recipe, advancement and translations.** Vanilla's hopper minecart is
  shapeless hopper + minecart; this would be filter hopper + minecart, the same shape.
* **Rendering.** A minecart draws its display block, which would be the filter hopper. Whether the
  filter item is also shown on the cart's sides, the way the block shows it, is open.

## Open questions

* **Does the filter show on the cart?** The block draws it so a sorter is readable. A moving cart is
  read differently, and drawing it costs a renderer per cart.
* **What happens to the filter when the cart breaks?** A minecart drops as a single item. The filter
  stack inside has to drop beside it, or the cart item has to carry it.

## Why it is not in the mod today

Proposed, and not designed yet: the question above is the design.
