# Configuration Reference

Illager Armada writes `config/illager_armada.json` (server settings, with relevant rendering choices used on the client). An existing file is retained and upgraded in place. Back it up before changing major pack settings. The file is rewritten during startup; restart the game/server after editing.

The values below are the **public 1.0.0 defaults**, not values supplied by any particular modpack. "ticks" means 20 ticks per second at 20 TPS.

## Ocean encounters

| JSON field | Default | Purpose |
| --- | ---: | --- |
| `naturalSpawningEnabled` | `true` | Enable periodic Overworld ocean encounters |
| `allowPeacefulSpawns` | `false` | Permit encounters in Peaceful mode |
| `naturalSpawnChance` | `0.20` | Chance per eligible 2-minute check; 20%, not 20 encounters |
| `encounterCheckTicks` | `2400` | Global check cadence; 2 minutes |
| `encounterCooldownTicks` | `2400` | Minimum time between successful encounters per player crew |
| `spawnMinDistance` | `80` | Preferred initial minimum separation, in blocks |
| `spawnMaxDistance` | `112` | Preferred maximum separation, in blocks |
| `loadedSpawnFallbackEnabled` | `true` | Permit shorter-distance search if distant sites are not loaded |
| `spawnFallbackMinDistance` | `64` | Lower limit for fallback spawn searches, in blocks |
| `minimumWaterDepth` | `5` | Required water depth in blocks; hull-specific drafts can require more |
| `nearbyPirateRange` | `256` | Distance for nearby-pirate cap, in blocks |
| `maxPiratesPerDimension` | `4` | Maximum active Armada ships, including all ships in fleets |
| `maximumPiratesNearVessel` | `1` | Blocks additional encounters while a pirate is already nearby |
| `naturalDespawnDistance` | `192` | Distance beyond which an unengaged pirate becomes eligible for cleanup |
| `naturalDespawnGraceTicks` | `1200` | Grace period before out-of-range cleanup; 60 seconds |
| `fleetsEnabled` | `true` | Permit Galleon-led four-ship fleets |
| `fleetEncounterChance` | `0.03` | Fleet chance after successful natural encounter roll when capacity permits |
| `maxFleetsPerDimension` | `1` | Maximum active Galleon fleets |
| `drakkarFleetOnly` | `true` | Do not choose a solo Drakkar for natural encounters |

Spawns require a player occupying a vanilla boat or Small Ships vessel (including a player properly standing aboard a ship), a valid **Overworld ocean** site and already-loaded water. Spectators and Creative players do not attract natural encounters. **20% every 2 minutes averages one eligible successful roll every 10 minutes**; it is not a fixed encounter interval. Server ship limits and safe spawn availability may extend the interval.

The 4-ship cap is intentional: a full fleet occupies all available active ship slots. Increase both ship and nearby caps cautiously; larger fleets multiply rendering, crew and cannon workloads.

## Ships, combat and loot

| JSON field | Default | Purpose |
| --- | ---: | --- |
| `hostileHullHealthMultiplier` | `1.10` | Pirate-only health factor, applied after configured Small Ships hull stats and installed upgrades. Valid 0.25–1.5 |
| `enemyShipUpgrades` | `true` | Install supported native Small Ships upgrades on new pirate ships |
| `gunnerAccuracyMultiplier` | `0.75` | Multiplier for gunner aim dispersion; **smaller = more accurate** |
| `disciplinedGunneryEnabled` | `true` | Prefer aligned, deliberate firing rather than uncontrolled volleys |
| `intelligentAmmoEnabled` | `true` | Choose solid, chained or grape shot by situation |
| `cannonMaxFireRange` | `65` | Maximum NPC firing distance in blocks |
| `chainShotMaxRange` | `38` | Maximum chain-shot distance in blocks |
| `antiOrbitTrackingEnabled` | `true` | Track close circling opponents using rudder control |
| `antiOrbitTrackingRange` | `60` | Maximum anti-orbit tracking distance, in blocks |
| `openWaterRetreatEnabled` | `true` | Escape via prechecked deep-water lanes |
| `fleetCollisionAvoidanceEnabled` | `true` | Predict nearby fleetmates and avoid their hulls |
| `ammoPerCannonMin` | `24` | Minimum ammunition granted per installed cannon at spawn |
| `ammoPerCannonMax` | `40` | Maximum ammunition granted per installed cannon at spawn |
| `ammoSalvageFraction` | `0.25` | Fraction of **remaining** ammo retained in the wreck (approximate) |
| `cargoRollMultiplier` | `1.0` | Scales datapack-defined cargo rolls; 0 disables cargo generation |
| `drakkarReinforcementsEnabled` | `true` | Allow fleet Drakkars to distribute finite reserve crew |

**Hull-health ordering:** configured Small Ships base HP × installed Iron Scantlings/other ship upgrades × `hostileHullHealthMultiplier` (pirates only). With Small Ships' stock +100% Iron Scantlings and Illager Armada's public 1.10 setting, a fully reinforced pirate has 2.20× its class's Small Ships base HP. Player ships use Small Ships' own health calculation without the Armada modifier.

The `shipGeneralCannonDamage` option in Small Ships concerns non-ship entity hits. Cannonball hull damage is calculated using Small Ships' projectile rules, including ammo type; Illager Armada does not change those base damage values.

## Presentation and sound

| JSON field | Default | Purpose |
| --- | ---: | --- |
| `bossBarsEnabled` | `true` | Show nearby engaged pirate boss bars (at most two per player) |
| `pirateSailsEnabled` | `true` | Generate distinctive pirate sail colours and insignia |
| `battleHornsEnabled` | `true` | Play tactical ship horn cues |
| `battleHornVolume` | `1.8` | Volume multiplier for ship battle horns |
| `intimidationHornsEnabled` | `true` | Use occasional intimidation horns during combat |
| `intimidationHornMinTicks` | `260` | Minimum intimidation interval: 13 seconds |
| `intimidationHornMaxTicks` | `440` | Maximum intimidation interval: 22 seconds |
| `encounterChatEnabled` | `true` | Send one directional chat notice when an encounter spawns |
| `encounterSoundEnabled` | `true` | Play one optional sound with the encounter notice |
| `distantCannonLodEnabled` | `true` | Skip expensive far-distance cannon models on the client |
| `cannonRenderLodDistance` | `72` | Cannon-render cutoff in blocks; 0 disables LOD distance culling |

`battleHornSounds` is a JSON object mapping signal names to Minecraft sound-event IDs. Use `"none"` to disable one specific event. Defaults:

| Signal | Sound ID |
| --- | --- |
| `sighted` | `minecraft:item.goat_horn.sound.2` |
| `ram` | `minecraft:item.goat_horn.sound.5` |
| `retreat` | `minecraft:item.goat_horn.sound.3` |
| `repair` | `minecraft:block.bell.use` |
| `strafe` | `minecraft:block.note_block.didgeridoo` |
| `broadside` | `minecraft:block.note_block.didgeridoo` |
| `rally` | `minecraft:item.goat_horn.sound.6` |
| `intimidate` | `minecraft:item.goat_horn.sound.0` (Ponder) |

## Fresh-install configuration

The initial release uses `configVersion: 1`. No compatibility or configuration migration from private development builds is provided. Install into a clean instance. On first launch, the mod generates defaults; afterwards, it reloads and validates the configuration at startup. Do not edit `configVersion` manually.

Other modpacks can lower the hostile-only health multiplier without globally changing Small Ships hulls. Modpack-specific `smallships-server.toml` health values are **not** shipped in Illager Armada's public JAR.

Performance: Fleet and navigation logic operates on already-loaded ships and chunks; no additional chunks are loaded or generated. NPC cannons and server AI still have real multiplayer rendering and entity costs, so increasing ship caps can be expensive.
