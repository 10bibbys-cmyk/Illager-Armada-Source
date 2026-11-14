# Illager Armada 1.0.1.1 — Stable Maintenance Release

**Minecraft 1.20.1 · Fabric · Small Ships 2.0.2 · Java 17**

Version **1.0.1.1** standardises the release numbering and makes the stable source available for public inspection. The pirate ships themselves behave exactly as they did in the working 1.0.1 gunnery hotfix.

## Updated
- Adopted the four-part version scheme: **1.0.1.1**.
- Added a clean public source repository with build instructions, All Rights Reserved permissions and Small Ships attribution.
- Refreshed the CurseForge description, dependency details and documentation.
- Made the build and release validator check the current version rather than a hard-coded version number.
- Removed the obsolete workflow targeting the earlier 1.0.1 GitHub release.

## Unchanged
- The 1.0.1 fix for the pirate-gunner scoreboard tag parsing crash.
- All ship classes, AI manoeuvres, broadside targeting and cannon ammunition behaviour.
- Fleet composition, finite crews, repairs, retreat and ramming decisions.
- Natural encounters, spawn chances, hull health, cargo loot tables and configuration defaults.
- All Java code, Mixins, gameplay resources and world persistence.

## Updating
Replace the previous Illager Armada JAR with **1.0.1.1** on both client and server. Keep your existing `config/illager_armada.json`; no configuration or world migration is required.

**Requirements:** Minecraft 1.20.1, Fabric Loader 0.15.0+, Fabric API, Small Ships 2.0.2 or newer (tested integration target: 2.0.2) and Java 17+.

**Source:** https://github.com/10bibbys-cmyk/Illager-Armada-Source

**Verification:** Stable 1.0.1.1 passed Java 17 CI and source validation. Its 35 compiled classes and all packaged resources match the tested 1.0.1 release byte-for-byte; only Fabric version metadata changed. No separate 1.0.1.1 in-game test is claimed.
