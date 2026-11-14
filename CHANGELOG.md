# Changelog

## 1.0.1.1 — Stable maintenance and source transparency

- Updated the release version to **1.0.1.1**, following the four-part version scheme.
- Published an independently inspectable public source snapshot and clarified All Rights Reserved source permissions.
- Updated the public description, build/release instructions and upstream Small Ships attribution.
- Made CI and source validation version-aware, retaining assertions for the 1.0.1 gunnery crash fix.
- Retired the obsolete GitHub workflow that could republish version 1.0.1 after later updates.
- **No gameplay modifications.** Naval AI, cannon aim, gunner skill, hull health, fleet behaviour, encounter probability, crew, loot, Mixins and configuration defaults remain identical to 1.0.1.
- Existing worlds and config files remain compatible; replace the previous mod JAR on both client and server.

## 1.0.1.1a — Release-standard Maintenance Alpha

- Prepared a four-part version number (Fabric metadata `1.0.1.1-alpha.1`) for the next release candidate.
- Made source validation and Java 17 CI use the actual Gradle version rather than hard-coding 1.0.1.
- Prepared updated CurseForge description, precise dependencies and a public-source licensing/attribution plan.
- Preserved the 1.0.1 gunnery crash correction and all existing naval gameplay, loot, AI logic, configuration and textures.
- **No Java source, Mixin, cargo JSON or default-config changes.** In-game regression check and successful CI build required before stable promotion.

## 1.0.1 — Gunnery crash hotfix

- Fixed a server-thread crash triggered when pirate gunners entered active combat (including switching to Survival near a hostile ship).
- Corrected scoreboard-tag parsing for per-gunner accuracy. The tag prefix `ia_accuracy_` is 12 characters; the prior build incorrectly started reading at index 15.
- Made captain confidence parsing use its prefix length as well to avoid similar indexing mistakes.
- No changes to ship navigation, encounter probability, ammunition balancing, cargo, hull health, AI tick frequency or world-save formats.
- Existing pirate crew and worlds remain compatible; replace the previous 1.0.0 JAR with 1.0.1.


## 1.0.0 — Initial public release

First supported release of **Illager Armada** for Minecraft 1.20.1, Fabric, Small Ships 2.0.2 and Java 17.

### Naval gameplay
- Seven native Small Ships hull types: Galley, Drakkar, Dhow, Caravel, Cog, Brigg and Galleon.
- Galleon-led four-ship pirate fleets with protective escorts and Drakkar crew reinforcement.
- Real finite crews: captains, first mates, engineers, cannon gunners and reserve sailors.
- Cannonball, chained-shot and grape-shot naval combat with native cannon fuses, predictive aim and finite ammunition.
- Broadside positioning, counter-orbit rudder tracking, fleet collision avoidance, safe-water retreats and bounded ramming.
- Persistent, hull-specific cargo generated from overrideable datapack manifests; native wreck salvage.
- Dynamic pirate banners and sail colours, boss bars, tactical goat horns, intimidation and directional encounter notices.

### Settings and performance
- 20% natural ocean encounter chance per eligible two-minute check, loaded-water-only spawn attempts and bounded fallback locations.
- Configurable pirate-only health modifier (public default 1.10), cannon ranges, AI discipline, fleet limits, loot, audio and display.
- Distant cannon model culling, staggered combat updates and cleanup of abandoned ships and crew.
- No new items or generated structures; no modpack-specific gameplay data bundled in the public JAR.

### First-release cleanup
- One unified ship controller for all seven hull classes.
- Removed abandoned prototype controller, development-mode ammunition refill and inactive assault-boat subsystem.
- Removed obsolete versioned role/entity tags, captain compatibility aliases, prior-version config migrations and developer-only commands.
- Added public installation, configuration, cargo-customization, troubleshooting and final QA documentation.

**Compatibility:** This is the first supported public version. Private test worlds and pre-release configurations are not supported for migration. Ships created in 1.0.0 still save and reload normally.
