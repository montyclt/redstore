# Technical conventions

## 1. Mod identity

| Field | Value |
| --- | --- |
| Display name | `Redstore` (working title — the public name may change before release) |
| Mod ID | `redstore` |
| Root package | `net.montyclt.redstore` |
| Author | montyclt |
| Repository directory | `RedstoreExtra` (kept as-is; it does not have to match the mod ID) |
| Environment | `*` — the mod adds blocks and a custom screen, so it **must** be installed on both the client and the server. It is not a server-side-only mod. |
| License | TBD (suggestion: MIT or LGPL-3.0) |

The name `Redstore` is treated as a stable **codename** for package and ID purposes. If the public
name changes later, the mod ID and package stay as they are, to avoid breaking existing worlds.

## 2. Toolchain

Verified on 2026-09-20 against the Fabric announcements, Modrinth, and the build files and
sources of the official Fabric documentation reference mod, which is the authority for the code
patterns quoted in these specs.

| Component | Version | Notes |
| --- | --- | --- |
| Minecraft | `26.3` | Unobfuscated since 26.1: Mojang's official names *are* the source names. |
| Java | **25** | Minimum for the Gradle JVM and the compiled bytecode since 26.1. |
| Gradle | `9.6.0` | Recommended by Fabric for 26.3. |
| Fabric Loom | `1.17` | Use the **`net.fabricmc.fabric-loom`** plugin (the non-remapping one). |
| Fabric Loader | `0.19.5` or newer | Declared as `>=0.19.5` in `fabric.mod.json`. |
| Fabric API | `0.161.0+26.3` | Latest at the time of writing. |
| Mappings | **Mojang official / unobfuscated** | Yarn is discontinued from 26.1 onward; there are no mappings to apply. |

Consequences of the unobfuscated toolchain that the build script must respect:

* Use `implementation` / `compileOnly` for mod dependencies — **not** `modImplementation` /
  `modCompileOnly`.
* Use the plain `jar` task — **not** `remapJar`.
* All class names in these specs are Mojang official names (`Block`, `BlockBehaviour.Properties`,
  `Identifier`, `AbstractContainerMenu`, …), never Yarn names.
* **`ResourceLocation` no longer exists**: 26.x calls it `net.minecraft.resources.Identifier`,
  built with `Identifier.fromNamespaceAndPath(ns, path)` or `Identifier.withDefaultNamespace(path)`.
* Client-only code lives in its own source set: `loom.splitEnvironmentSourceSets()` plus a `mods`
  block, so client classes go in `src/client/java` and are never loaded on a dedicated server.
* Access wideners have been replaced by **ClassTweaker**: the file is `redstore.classtweaker`,
  pointed at by `loom.accessWidenerPath` and declared in `fabric.mod.json` under the
  `"accessWidener"` key. The mod currently needs none.

`gradle.properties` skeleton:

```properties
org.gradle.jvmargs=-Xmx2G
minecraft_version=26.3
loader_version=0.19.5
fabric_version=0.161.0+26.3
loom_version=1.17
mod_version=0.1.0
maven_group=net.montyclt
archives_base_name=redstore
```

## 3. Package layout

Client-only classes live in their own source set (`splitEnvironmentSourceSets`), so they cannot be
loaded on a dedicated server.

```
src/main/java/net/montyclt/redstore
├── Redstore.java                      # ModInitializer: MOD_ID, id(String), registry bootstrap
├── block/
│   ├── RedstonePlateBlock.java        # abstract: form, facing, face reading, output
│   ├── gate/
│   │   ├── LogicGateBlock.java        # extends RedstonePlateBlock; the three gates are instances
│   │   └── GateOperation.java         # enum AND / OR / XOR
│   ├── RedstoneClockBlock.java        # extends RedstonePlateBlock
│   ├── FilterHopperBlock.java
│   └── ChunkLoaderBlock.java
├── blockentity/
│   ├── FilterHopperBlockEntity.java
│   ├── HopperLogic.java               # port of vanilla's hopper transfer algorithm
│   └── ChunkLoaderBlockEntity.java
├── menu/
│   └── FilterHopperMenu.java
├── chunkloading/
│   ├── ChunkLoaderManager.java        # ticket add/remove, deferred to the server thread
│   └── ChunkLoaderSavedData.java      # per-dimension persistent record of our loaders
├── registry/
│   ├── RedstoreBlockIds.java          # BlockItemId per block
│   ├── RedstoreBlocks.java
│   ├── RedstoreBlockEntities.java
│   └── RedstoreMenus.java
└── datagen/                           # deferred, see §10

src/client/java/net/montyclt/redstore
├── RedstoreClient.java                # ClientModInitializer: screens, render layers
└── client/screen/FilterHopperScreen.java
```

Resources:

```
src/main/resources/
├── fabric.mod.json
├── redstore.classtweaker           # only if a widened member turns out to be unavoidable
├── redstore.mixins.json            # only if a mixin turns out to be necessary
├── assets/redstore/...             # blockstates, models, item models, lang, gui
└── data/redstore/...               # recipe/, loot_table/, tags/
```

## 4. Naming rules

* Resource IDs: `snake_case`, always namespaced `redstore:`.
* Registry holder classes expose `public static final` fields named after the ID in
  `SCREAMING_SNAKE_CASE` (`RedstoreBlocks.AND_GATE`).
* One `Redstore.id(String path)` helper returning an `Identifier` is the only place where the
  namespace string is written.
* Block registration follows the current Fabric pattern for 26.x, confirmed against the reference
  mod: a `BlockItemId` per block (`BlockItemId.create(id, id)` from `net.minecraft.references`),
  plus the two `register()` overloads — one taking `ResourceKey<Block>` and calling
  `properties.setId(id)` before constructing the block, one taking `BlockItemId` and additionally
  registering a `BlockItem` with `new Item.Properties().useBlockDescriptionPrefix().setId(id.item())`.

## 5. Block registry bootstrap

`Redstore#onInitialize` must, in this order:

1. `RedstoreBlocks.initialize()` → blocks + block items.
2. `RedstoreBlockEntities.initialize()`.
3. `RedstoreMenus.initialize()`.
4. `ChunkLoaderManager.initialize()` → registers the server lifecycle listeners.

`RedstoreClient#onInitializeClient` registers `MenuScreens.register(RedstoreMenus.FILTER_HOPPER,
FilterHopperScreen::new)`.

## 6. Creative tabs

**The mod has no tab of its own.** Its items are appended to the vanilla tabs where a player would
look for them:

| Tab | Items | Order |
| --- | --- | --- |
| **Redstone Blocks** (`CreativeModeTabs.REDSTONE_BLOCKS`) | AND gate, OR gate, XOR gate, redstone clock, filter hopper | in that order |
| **Functional Blocks** (`CreativeModeTabs.FUNCTIONAL_BLOCKS`) | chunk loader | — |

Appending is done with `CreativeModeTabEvents.modifyOutputEvent(...)` — the Fabric API class is
`net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents`, not the older `ItemGroupEvents`.

### 6.1 Why no tab of our own

A tab is a permanent slot in a bar every mod shares. A five-block mod claiming one costs every
player who installs it a row of the creative menu, and it buys them nothing: they already know
where a repeater lives, and that is where these blocks belong. A tab earns its place when a mod
adds enough that its items would drown in vanilla's; six blocks do not.

The blocks are also easier to *find* this way, next to the vanilla components they imitate, which
is the whole point of a mod that promises no new rules.

### 6.2 Why the chunk loader is the exception

It is not a redstone component: it takes no signal, emits none, and no circuit contains one. In
vanilla's own jar the redstone tab is where the repeater, the comparator and the hopper sit
together — so the filter hopper and the plates belong there — while the beacon, the conduit, the
ender chest and the lodestone share the functional tab. Those four are exactly the chunk loader's
neighbours: placed utilities that quietly change what the world does around them.

## 7. Translations

Two language files are maintained from the start, generated by datagen providers:

* `assets/redstore/lang/en_us.json`
* `assets/redstore/lang/es_es.json`

Key scheme:

| Key | en_us | es_es |
| --- | --- | --- |
| `block.redstore.and_gate` | AND Gate | Puerta AND |
| `block.redstore.or_gate` | OR Gate | Puerta OR |
| `block.redstore.xor_gate` | XOR Gate | Puerta XOR |
| `block.redstore.redstone_clock` | Redstone Clock | Reloj de redstone |
| `block.redstore.filter_hopper` | Filter Hopper | Tolva filtrante |
| `block.redstore.chunk_loader` | Chunk Loader | Cargador de chunks |
| `container.redstore.filter_hopper` | Filter Hopper | Tolva filtrante |
| `gui.redstore.filter.mode.whitelist` | Whitelist: only the filtered item enters | Lista blanca: solo entra el ítem filtrado |
| `gui.redstore.filter.mode.blacklist` | Blacklist: everything but the filtered item enters | Lista negra: entra todo menos el ítem filtrado |
| `gui.redstore.filter.strict.on` | Strict: components must match | Estricto: los componentes deben coincidir |
| `gui.redstore.filter.strict.off` | Loose: item type only | Flexible: solo el tipo de ítem |
| `gui.redstore.filter.slot_hint` | Filtered item | Ítem filtrado |
| `message.redstore.clock.mode.square` | Square wave | Onda cuadrada |
| `message.redstore.clock.mode.pulse` | 1-tick pulse | Pulso de 1 tick |
| `message.redstore.chunk_loader.status` | Chunk [%s, %s] in %s is loaded | El chunk [%s, %s] en %s está cargado |
| `tooltip.redstore.<block>.1` | What the block is | Qué es el bloque |
| `tooltip.redstore.<block>.2` | What a click does to it | Qué hace un clic sobre él |

Player-facing runtime messages are sent as **action bar** text, not chat.

### 7.1 Item tooltips

Every block item carries **exactly two lines** of tooltip, under
`tooltip.redstore.<block>.1` and `.2`: the first says what the block is, the second what a click
does to it. Two is the budget, not a minimum — a tooltip that runs to a paragraph is a wiki page
in the wrong place, and the block's spec is where the whole rule lives.

They are attached as the vanilla **lore component** on the block item's `Item.Properties`, with
the lines handed over already styled in grey:

```java
new Item.Properties()
        .useBlockDescriptionPrefix()
        .component(DataComponents.LORE, new ItemLore(lines, lines))
        .setId(id.item())
```

Overriding `Item#appendHoverText` would do the same thing, but 26.3 deprecates it and no vanilla
item overrides it any more. `ItemLore`'s two-argument constructor takes the saved lines and the
rendered ones separately, which is what keeps the hint grey instead of the purple italics lore
normally gets.

## 8. Tags

| Tag | Members | Purpose |
| --- | --- | --- |
| `redstore:logic_gates` | the three gates | Convenience for recipes/future features. |
| `redstore:redstone_plates` | the three gates + the redstone clock | Every flat, repeater-shaped Redstore component. |
| `minecraft:mineable/pickaxe` | all six blocks | Correct tool. |
| `minecraft:needs_stone_tool` | `redstore:chunk_loader` | Only the chunk loader requires a tool for drops. |

## 9. Sounds

No custom sound events. The mod reuses:

* `SoundEvents.COMPARATOR_CLICK` — gate and clock mode toggles.
* `SoundEvents.STONE_PLACE` / `SoundType.STONE` — gate place/break.
* `SoundType.METAL` — filter hopper and chunk loader.

## 10. Derived assets

Some assets are neither original art nor datagen output: they are vanilla files with a small,
precisely defined edit. So far:

* **Filter hopper** — a vanilla hopper with the rim of its mouth recoloured to the item frame's
  wood, plus its two block models with the texture references swapped.
* **Redstone clock** — the vanilla repeater plate with a mode badge and a clock dial engraved on
  it, the repeater's own delay models with nothing changed but their textures, and an inventory
  icon made from the repeater's 3/4 sprite.
* **Logic gates** — the same plate with the painted redstone line rubbed out and a panel of the
  gate's metal set into it, the repeater's torch copied to three positions, and an icon made from
  the comparator's sprite.

They are produced by the Gradle task **`generateAssets`**, implemented in `buildSrc/` and run
automatically before `processResources`, so the jar and the development client both get them with
no extra step. It reads the Minecraft jar Loom has already downloaded and writes into
`build/generated/assets`, which is declared as an extra resource directory of the main source set.

Glyphs are authored as ASCII art in the task, one character per pixel, so a new plate costs a few
lines rather than a new PNG and the diff shows the pixels that changed.

### 10.1 Why they are generated and never committed

Two reasons, and the second is the binding one.

**The edit stays reviewable.** A recolouring expressed as code can be read, argued with and
replayed when Mojang retouches an original. A PNG in the repository can only be replaced.

**The repository must not contain modified Mojang art.** Mojang's usage guidelines permit editing
the default textures for personal use but not redistributing them, in original or modified form.
Committing a recoloured `hopper_top.png` would be redistributing one. Generating it instead means
the repository holds the *code that describes the edit* — our own expression — and every copy of
the texture is produced on a player's own machine from the game they already own.

This is also why **the mod is distributed as source rather than as a jar**: a built jar contains
the derived textures, so handing one to another player would put the problem straight back. Anyone
who wants to play builds it themselves, which the build makes a single command.

An earlier draft committed the generated files, with the reasoning that "the build must not depend
on a jar sitting in someone's cache". That reasoning does not hold: the build already depends on
Loom having downloaded Minecraft, so the jar is always there when the task runs.

### 10.2 Consequences to keep in mind

* The task's only prerequisite is the JDK the build already needs. An earlier version was a Python
  script, which would have meant asking every player to install Python as well; Java's `ImageIO`
  reads and writes PNG out of the box, so the port also deleted a hand-rolled PNG codec.
* Declare the task's inputs and outputs so Gradle can skip it when nothing changed. Without that
  it rewrites every asset on every build and forces a resource copy each time.
* Running the client straight from an IDE's own run configuration can skip Gradle tasks entirely,
  and then the assets are never generated. Set the IDE to build with Gradle, or launch with
  `./gradlew runClient`.
* Everything that reads the main resources must depend on the task — `processResources` and
  `sourcesJar` both do, and Gradle fails the build if one is forgotten.

## 11. Data generation

Deferred: the first implementation writes its block state, recipe, loot table, tag and
recipe-unlock advancement JSON by hand, because there is one block and datagen is a whole extra
source set. **The advancements are the trap here**: datagen emits one per recipe automatically, so
writing recipes by hand means remembering that a recipe without its unlock advancement is craftable
but never appears in the crafting table's book. That was shipped broken once. The rule below applies from the
moment a second block lands. Derived assets (section 10) stay outside datagen either way: they
depend on vanilla's own files, not on our registry.

Everything that can be generated is generated (`fabric-datagen-api-v1`), into
`src/main/generated`: block state definitions, block and item models, loot tables, recipes,
advancement-free recipe unlocks, tags and both language files. Only raw textures and the GUI sprite
sheet are authored by hand.
