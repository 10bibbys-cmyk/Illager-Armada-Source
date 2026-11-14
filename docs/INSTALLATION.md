# Installation, Updating and Multiplayer

## Requirements

- Minecraft **1.20.1**
- Fabric Loader **0.15+**
- Fabric API compatible with Fabric 1.20.1
- **Small Ships 2.0.2**, using a compatible Fabric 1.20.1 build
- Java **17+**

Install Illager Armada and Small Ships **on the server and on every connecting client**. Do not install an older Illager Armada JAR beside the new one; replace it.

## Installation

1. Use a **fresh 1.20.1 instance/world** for the first public release. Private test saves and pre-release configurations are unsupported.
2. Ensure `mods/` does not contain an older private testing JAR.
3. Copy the new `illager-armada-1.0.0.jar` into `mods/` on server and clients.
4. Start the game/server. `config/illager_armada.json` is generated if it does not already exist.
5. Check the newly generated default settings and change them as needed, then restart.
6. Enter an Overworld ocean in a crewed vessel and verify a manual `/illagerarmada encounter` before leaving natural spawning enabled for long-term testing.

## New-world support

**Illager Armada 1.0 is the first public version.** The mod does not migrate pirate NPCs, hull tags, inventories or configuration from the private development builds. Starting a new world is recommended for the first release build; do not treat test worlds as supported upgrade sources.

All seven Small Ships hull types can be owned by players, including the Caravel. Armada only changes ships it spawns and marks as hostile. The mod does not replace native Small Ships items or change ordinary player ships.

If balancing Small Ships for a modpack, edit that pack's **current** `smallships-server.toml` separately. PotA-specific health values are not included in the public JAR.

## Dedicated server and clients

Server owns encounters, AI decisions, cannon fuses, loot, crew and health. Clients render ships, gun models and the HUD. Both sides must have Illager Armada so client rendering optimizations and synced content behave correctly. The mod has no new custom items or world generation.

## Operator tests

Use `/illagerarmada encounter` aboard a ship to test a single raider; `/illagerarmada fleet` tests the four-ship formation. Both announce successful spawns to that vessel's players. These commands bypass natural spawn chance/cooldown but still require safe, loaded ocean water and enough ship capacity.

Use `/illagerarmada perf` to sample **Armada AI callbacks only**; it does not include Small Ships hull physics, rendering, shader work or other mods. `/illagerarmada clearall` administratively removes tracked pirates and their assigned crew, not player ships.

## Troubleshooting

- **No natural encounters:** Ensure a non-Creative, non-Spectator player is sailing in a recognized vessel in an Overworld ocean; check difficulty, loaded deep-water area, the existing fleet/ship cap and whether the previous encounter is still within the cooldown period. A 20% chance can fail for many consecutive checks.
- **No cannon fire:** Confirm the ship has real remaining ammunition, at least one living assigned gunner, and a valid broadside at a sensible range. Gunners deliberately hold fire rather than fire wildly.
- **Ship motion:** Use a compatible Small Ships build. The NPC helm depends on Small Ships' current `canDrive` and sail controls.
- **Lag with fleets:** Profile client and server separately. Ship models, particles and native Small Ships ticks can be more expensive than Illager Armada's control logic. Try reducing the number of active ships or enabling the cannon rendering LOD.
- **Stale config:** Use the server file `config/illager_armada.json`, check it remains valid JSON, restart, and inspect any error reported by Illager Armada.

## Maintenance

For public issues, include Minecraft, Fabric Loader, Fabric API and Small Ships versions, the Illager Armada JAR version, relevant configuration entries, a minimal reproduction and logs. Include Spark profiles for actual performance complaints. Do not publish world backups or player information without review.
