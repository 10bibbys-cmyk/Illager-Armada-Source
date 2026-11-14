# Illager Armada

**Stable release: 1.0.1.1 · Minecraft 1.20.1 · Fabric · Small Ships 2.0.2 · Java 17**

This maintenance release adopts the four-part version scheme and publishes independently inspectable source. The Java implementation, gameplay resources, configuration schema and balancing are unchanged from the proven 1.0.1 gunnery hotfix.


Illager Armada introduces intelligent, crewed pirate vessels to the oceans. Enemy ships sail, position for broadsides, operate real Small Ships cannons, defend their crews, retreat and sometimes appear as a coordinated Galleon-led fleet. Defeat the pirates to salvage stolen cargo and ammunition.

**Status:** 1.0.1.1 retains the tested 1.0.1 stability hotfix for the 1.0.0 pirate gunnery crash. The original crash scenario was reproduced, corrected and confirmed working in-game. Existing ships and configurations remain supported.

## Gameplay

- **Seven hull classes:** Galley, Drakkar, Dhow, Caravel, Cog, Brigg and Galleon. Galleons appear only as fleet flagships; Drakkars normally accompany fleets.
- **Real finite crews:** Captains, first mates, engineers, gunners and reserves occupy native Small Ships seats. Crew members can replace fallen specialists; reserve sailors are not generated infinitely.
- **Deliberate naval gunnery:** Individually skilled gunners predict the opponent's movement and cannonball flight, track the target through the fuse and fire within the cannon's true firing arc. Cannonballs damage hulls, Chained Shot attacks closer-range masts, and Grape Shot threatens nearby crews. Every shot uses inventory ammunition.
- **Naval decisions:** Ships establish firing lanes, turn their rudder to defend against circling opponents, avoid fleet collisions, attempt limited ramming, and seek safe open-water routes when retreating.
- **Fleets and reinforcements:** A rare Galleon fleet includes its flagship, a guard vessel, an attacking escort and a Drakkar troop carrier, coordinating finite reserve crew between ships.
- **Salvage and atmosphere:** Persistent cargo, native wrecks, limited ammunition recovery, individual sail colours, battle horns, intimidation calls, boss bars and one-time encounter announcements.
- **Performance-conscious implementation:** Server-managed AI with throttled updates and loaded-chunk-only water checks, plus an optional distant-cannon render optimization.

Illager Armada adds no new items or world-generation structures and does not redistribute Small Ships assets.

## Installation

Install on **both server and clients** with the following dependencies:

| Dependency | Requirement |
| --- | --- |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.15.0 or newer |
| Fabric API | Compatible with 1.20.1 |
| Small Ships | Compatible 1.20.1 build, 2.0.2 or newer |
| Java | 17 or newer |

For a fresh installation, copy the JAR to `mods/` and launch. Configuration will be created at `config/illager_armada.json`. This first-release build does **not** provide migration or compatibility with unpublished private development versions or private testing worlds. Persistent vessels created by 1.0.0 can be saved and reloaded normally.

See [Installation](docs/INSTALLATION.md) for setup and multiplayer troubleshooting.

## Natural encounters

The public defaults make a **20% spawn roll every two minutes** for each eligible player vessel in an Overworld ocean, with a two-minute minimum encounter cooldown. Under continuously suitable conditions, the expected interval between successful rolls is ten minutes; it is not guaranteed. Natural encounters require valid already-loaded water and respect ship caps, difficulty and player eligibility.

A successful encounter announces its compass direction and ship class (configurable). Force single encounters or entire fleets with operator commands.

## Configuration

The public default `hostileHullHealthMultiplier` is **1.10**. It scales hostile ships only, *after* native Small Ships upgrades. Custom packs can lower it to 0.60 or another value without affecting ordinary player ships or changing Small Ships' own base hull settings.

The public mod does **not** ship another modpack's customized Small Ships TOML or pirate-loot overrides.

See [Configuration reference](docs/CONFIGURATION.md) and [Pirate cargo datapacks](docs/LOOT-DATAPACKS.md).

## Operator commands

All commands require permission level 2:

| Command | Purpose |
| --- | --- |
| `/illagerarmada encounter` | Force a single natural-style ocean encounter with a one-time directional announcement |
| `/illagerarmada fleet` | Force a four-ship Galleon fleet at a valid loaded site |
| `/illagerarmada summonship <galley\|drakkar\|dhow\|caravel\|cog\|brigg>` | Summon a particular solo hull near the operator |
| `/illagerarmada summon <x> <y> <z>` | Summon a Caravel at a loaded valid water position |
| `/illagerarmada status` | Inspect the nearest pirate ship |
| `/illagerarmada perf` | Report pirate AI timings, excluding native Small Ships rendering and physics |
| `/illagerarmada clear` / `clearall` | Administratively remove pirates and assigned crew |
| `/illagerarmada scuttle [seconds]` | Sink the nearest pirate now or after a delay |
| `/illagerarmada confidence <1–100>` | Set diagnostic captain confidence |
| `/illagerarmada mode <mode>` | Force a diagnostic AI mode; `auto` restores normal behaviour |

The `encounter` and `fleet` commands announce **successful** spawns only.

## Release information

See [Changelog](CHANGELOG.md), [Configuration reference](docs/CONFIGURATION.md), [Installation](docs/INSTALLATION.md) and [Loot datapacks](docs/LOOT-DATAPACKS.md). The stable 1.0.1.1 build passed CI checks and preserves the existing gameplay implementation. Dedicated-server and full-fleet multiplayer smoke testing are still recommended for new modpack combinations.

**Public source:** https://github.com/10bibbys-cmyk/Illager-Armada-Source (source-available, All Rights Reserved; read `LICENSE` and `NOTICE.md`).

**License:** All Rights Reserved. No third-party assets are bundled.
