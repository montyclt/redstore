# Server configuration

## 1. File

* Path: `config/redstore.json` (relative to the server or client instance directory).
* Written with defaults the first time the mod starts if the file does not exist.
* Parsed with a `Codec` built from a Java `record`; unknown keys are ignored, missing keys fall back
  to the default, and a malformed file logs an error and falls back to defaults **without**
  overwriting the file.
* Loaded once on `onInitialize`, and re-read by `/redstore reload` (operator only, permission
  level 2).
* The configuration is **server-authoritative**. The client parses its own copy only for cosmetic
  values (`chunkLoader.particles`); every rule that affects gameplay is enforced on the server.

## 2. Defaults

```json
{
  "logicGates": {
    "enabled": true
  },
  "clock": {
    "enabled": true,
    "allowPulseMode": true
  },
  "filterHopper": {
    "enabled": true,
    "allowStrictMatching": true
  },
  "chunkLoader": {
    "enabled": true,
    "maxPerPlayer": 8,
    "maxTotal": -1,
    "opsBypassLimits": true,
    "allowedDimensions": [],
    "particles": true
  }
}
```

## 3. Keys

### `logicGates`

| Key | Type | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | When false: the three recipes are disabled, the items are hidden from the creative tabs and placement is rejected with `message.redstore.disabled`. Blocks already placed keep working, so disabling the feature never corrupts an existing world. |

### `clock`

| Key | Type | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Same semantics as `logicGates.enabled`. |
| `allowPulseMode` | boolean | `true` | When false: sneak + right-click no longer switches to 1-tick pulse mode. Clocks already in pulse mode keep pulsing; they are simply frozen in that mode. |

### `filterHopper`

| Key | Type | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Same semantics as `logicGates.enabled`. |
| `allowStrictMatching` | boolean | `true` | When false: the strict-matching button is greyed out and `clickMenuButton(1)` is ignored server-side. Hoppers already set to strict fall back to loose matching while the setting is off. |

### `chunkLoader`

| Key | Type | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | When false: recipe disabled, creative entry hidden, placement rejected, **and** every existing loader releases its chunk on server start (the blocks stay in the world, dormant). |
| `maxPerPlayer` | int | `8` | Maximum loaders a single player may have placed at once. `-1` = unlimited, `0` = nobody may place any. |
| `maxTotal` | int | `-1` | Maximum loaders in the whole server across all dimensions. `-1` = unlimited. |
| `opsBypassLimits` | boolean | `true` | Players with permission level ≥ 2 ignore `maxPerPlayer` (but not `maxTotal`). |
| `allowedDimensions` | list of dimension IDs | `[]` | Empty = every dimension. Otherwise only the listed dimensions accept chunk loaders, e.g. `["minecraft:overworld", "minecraft:the_nether"]`. |
| `particles` | boolean | `true` | Client-side cosmetic particles above active loaders. |

## 4. Recommended presets

**Small survival server** — the defaults.

**Large technical server** (chunk loading is the only real cost):

```json
{ "chunkLoader": { "maxPerPlayer": 4, "maxTotal": 200, "allowedDimensions": ["minecraft:overworld", "minecraft:the_nether"] } }
```

**Redstone-only creative server** (keep the gates, drop the chunk loading):

```json
{ "chunkLoader": { "enabled": false } }
```

## 5. Commands

| Command | Permission | Effect |
| --- | --- | --- |
| `/redstore reload` | 2 | Re-read `config/redstore.json` and re-apply chunk-loader caps. |
| `/redstore loaders list` | 2 | List every registered chunk loader: dimension, chunk, position, owner. |
| `/redstore loaders count [player]` | 2 (0 for your own count) | How many loaders a player has placed. |

The `loaders` subcommands are optional polish; see [roadmap.md](roadmap.md).
