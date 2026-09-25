# Idea: a filter that holds more than one item

**Status:** proposed on 2026-09-22. Not committed to.
**Would belong to:** [../blocks/filter-hopper.md](../blocks/filter-hopper.md), as a change to a
block that is already built.
**Shared context:** [README.md](README.md).

## What it does

The filter holds several items instead of one, so a single hopper can accept a family: everything
made of wood, every kind of log, every colour of wool.

## Its vanilla equivalent, and what it costs there

**One sorter cell per item.** Filtering ten items is ten cells in a row, each its own hopper,
comparator, torch and repeater. A filter that holds all ten compresses those cells into one block,
which is squarely what the mod is for.

## What it is worth

Anything that is really one category spread across many items. Wood is the obvious one: logs,
planks, slabs, stairs, fences, doors, trapdoors and the rest are each a separate item, and a player
who wants "the wood" wants all of them in one place.

## The question that has to be answered first: the limit

A hopper has **five slots** to pass items through, and it is worth being precise about what that
does and does not constrain.

**It does not cap how many entries a filter can have.** A filter of ten wood items on a five-slot
hopper works: at any moment up to five different accepted items are inside, the rest wait, and over
time all ten pass.

**It caps how many different accepted items can be inside at once.** An item can only enter if there
is room for it — an empty slot, or a stack of the same item that is not full. With several accepted
items the five slots are shared between them. That is not a new failure, a vanilla hopper does the
same with anything, but a single-item filter never meets it, because every slot holds the same
item.

So the cap on entries has to be chosen on other grounds. The tidy answer is **five**, one row of
filter slots matching the five storage slots: a number the block already has, rather than one
invented for it.

## Other ways to get there

**Item tags.** Vanilla already groups wood — `logs`, `planks`, `wooden_slabs`, `wooden_stairs`,
`wooden_fences`, `wooden_doors`, `wooden_trapdoors`, `wooden_buttons`, `wooden_pressure_plates`,
`wooden_shelves`, and one `*_logs` tag per species. A tag answers *part* of the example in one
entry: "all planks" is a single tag.

But it costs two things. There is **no single tag for everything made of wood**, so the example
still needs several. And a player cannot *hold* a tag: putting one in a slot needs some item to stand
for it and a rule that says which, and that rule exists nowhere in vanilla (design rule 4 in
[../README.md](../README.md)).

## Open questions

* **Do strict/loose and whitelist/blacklist apply to the whole list?** Per-entry modes would be
  more flexible and much harder to read.
* **What is drawn on the sides?** The block shows its one filter item so a sorter is readable at a
  glance. With five, the choices are the first one, all of them, or cycling — and cycling is the one
  a builder cannot read quickly.
* **The menu grows.** Today the left of it holds the filter slot and the two toggles, then a gap,
  then the five storage slots. A row of five filter slots needs room of its own.
* **Existing hoppers have to load.** The single filter is saved under `Filter`; a list is a new key,
  and the old one has to be read as a one-entry list.

## Why it is not in the mod today

Proposed, and the limit above is the first thing to decide.
