# Illager Armada

**The seas are no longer safe.**

**Illager Armada** brings hostile pirate ships, finite pirate crews and coordinated naval combat to Minecraft **1.20.1 Fabric**, built around the real vessels and cannons of **Small Ships 2.0.2**.

Explore the ocean and you may cross paths with an Illager patrol. Small raiders can ambush unsuspecting sailors, while larger warships and rare Galleon-led fleets challenge experienced crews.

## Seven Pirate Ship Types

- **Galley:** A compact raider for close encounters.
- **Drakkar:** A fast support vessel that transports reserve crew in fleets.
- **Dhow:** A manoeuvrable skirmishing vessel.
- **Caravel:** A balanced patrol and combat ship.
- **Cog:** A sturdy cargo-heavy vessel with valuable salvage.
- **Brigg:** A powerful, heavily armed warship.
- **Galleon:** A fleet-only flagship with multiple escorts.

## Intelligent Naval Combat

Pirate captains position for broadsides, track opponents attempting to circle them, avoid nearby allied ships, attempt limited rams and retreat or regroup when outmatched. Crewed cannons calculate moving-target aim, respect their firing arcs and use **Small Ships' actual cannon firing, fuses and ammunition consumption**.

Gunners can choose **Cannonballs, Chained Shot or Grape Shot**, depending on range and target. Accuracy varies with each gunner's skill and the mod's settings.

## Finite, Physical Crews

Captains, gunners, engineers and reserves occupy real Small Ships positions. The first mate can succeed a fallen captain, gunners can be replaced by reserves and engineers can carry out limited repairs. Pirate ships do not create infinite replacement sailors during combat.

## Rare, Coordinated Fleets

A Galleon-led encounter contains **four vessels**: the flagship, protective escort, attacking ship and Drakkar troop transport. They coordinate manoeuvres and finite crew reinforcements. Fleet size and frequency are configurable.

## Cargo and Shipwreck Salvage

Each hull class has its own configurable cargo manifest. Defeated pirates leave remaining cargo in native Small Ships wrecks, with a limited portion of surviving ammunition available for salvage.

Use datapacks to override the included vanilla-only manifests at `data/illager_armada/pirate_cargo/<hull>.json`. Larger vessels generally provide more cargo rolls.

## Natural Ocean Encounters

By default, each eligible player-crewed vessel has a **20% encounter roll every 2,400 ticks (two minutes at 20 TPS)** in an Overworld ocean, subject to loaded-water spawn conditions, ship limits and cooldowns. Under continuously eligible conditions, this averages one successful roll every ten minutes, but does not guarantee a spawn or a fixed interval.

Successful encounters can announce the direction and play an audible signal. Pirate count, fleet chance, difficulty and other settings are configurable via `config/illager_armada.json`.

## Additional Features

- Different pirate sail colours and insignia, plus configurable battle horns
- Optional pirate boss bars
- Configurable pirate-only hull health, AI accuracy and cannon ranges
- Staggered server AI and optional client distant-cannon rendering optimization
- Multiplayer combat against player-crewed vessels
- Operator commands for controlled single-ship and fleet testing
- No new world-generation structures, custom items or bundled Small Ships assets

## Requirements

- **Minecraft:** 1.20.1
- **Mod loader:** Fabric Loader **0.15.0+**
- **Fabric API:** Required
- **Small Ships:** **2.0.2 or newer** (tested target is 2.0.2)
- **Java:** 17+
- **Environment:** Both client and server

Install Illager Armada with its dependencies on both sides when playing multiplayer.

This is an **independent Small Ships addon**, not an official Small Ships component. Player-owned ships are not subject to Illager Armada's pirate-only hull health modifier.

## Public Source Code & Licensing

Illager Armada's **1.0.1.1** source is available for transparency and private compatibility verification:

**Source repository:** https://github.com/10bibbys-cmyk/Illager-Armada-Source

The independently owned addon code is **source-available, All Rights Reserved**, rather than released under a conventional open-source licence. Inspecting or privately compiling the code is permitted under the repository's [LICENSE](https://github.com/10bibbys-cmyk/Illager-Armada-Source/blob/main/LICENSE). Redistributing the source, republishing modified builds, or incorporating it into other distributed projects requires prior written permission, except where applicable law or other valid licences grant rights.

Small Ships is an external required dependency. Its code and artwork are not bundled or relicensed by this addon. See the repository's [third-party notice](https://github.com/10bibbys-cmyk/Illager-Armada-Source/blob/main/NOTICE.md).

**Take to the seas, prepare your cannons, and survive the Armada.**

**AI-assisted development disclosure:** AI-assisted tools supported development and documentation. The implementation was reviewed and tested by its developer.
