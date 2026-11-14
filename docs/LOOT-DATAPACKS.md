# Pirate Cargo Datapack Format

Illager Armada provisions a newly spawned pirate ship once, directly into its real Small Ships inventory. Custom loot comes from data resources at:

`data/illager_armada/pirate_cargo/<hull>.json`

Supported hull file names: `galley`, `drakkar`, `dhow`, `caravel`, `cog`, `brigg`, `galleon`. A datapack may replace any file; for packs using KubeJS, use the equivalent `kubejs/data/illager_armada/pirate_cargo/<hull>.json`. Use ordinary Minecraft datapack loading rules to set precedence.

## Schema

```json
{
  "_comment": "An example independent cargo profile",
  "pools": [
    {
      "rolls_min": 2,
      "rolls_max": 5,
      "crew_bonus": true,
      "entries": [
        {"item": "minecraft:emerald", "weight": 6, "min": 1, "max": 3},
        {"item": "minecraft:iron_ingot", "weight": 10, "min": 2, "max": 6},
        {"item": "minecraft:diamond", "weight": 1, "min": 1, "max": 1}
      ]
    }
  ]
}
```

Each pool independently rolls between `rolls_min` and `rolls_max` entries, inclusive. If `crew_bonus` is true, the starting crew can add up to three additional rolls to that pool. The server multiplies the result by `cargoRollMultiplier`, rounds up and clamps it to 0–32 rolls **per pool**. Entries are selected by non-negative integer `weight`; an item selected with weight 10 is ten times as likely as weight 1 *within that pool*, assuming both valid.

An entry's `item` is a Minecraft or modded item registry ID. The count is uniformly random from `min` to `max`, inclusive (capped at 256 before inventory insertion). Items are merged into compatible stacks and then placed in empty hold slots. When the inventory fills, the excess cannot be inserted.

Starting cannon ammunition is provisioned **before** cargo from the manifest. It is finite and consumes real cannonball items. Once a ship is provisioned, the persistent `ia_provisioned` tag prevents duplicate cargo, including after save/reload. Editing a manifest affects only *newly spawned* ships, not pre-existing inventories.

When the ship sinks, remaining cannon ammunition is reduced by the configured salvage fraction. Other treasure remains in the native Small Ships wreck. Iron Scantlings and regular weapon damage are unrelated to cargo JSON.

## Validation

Keep `rolls_min <= rolls_max`, `min <= max`, positive item quantities, nonnegative weights and valid item IDs. A missing or malformed manifest is reported in server logs and does not trigger an infinite retry loop. Preserve each ship's file name and test fresh spawns after datapack changes.

Built-in manifests are vanilla-only for broad modpack compatibility. Illager Armada does **not** require a modpack-specific loot datapack.
