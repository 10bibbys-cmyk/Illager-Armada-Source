#!/usr/bin/env python3
"""Source-only release checks. No network or Minecraft runtime required."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src" / "main"
JAVA = MAIN / "java" / "dev" / "illagerarmada"
RESOURCE = MAIN / "resources"
HULLS = ("galley", "drakkar", "dhow", "caravel", "cog", "brigg", "galleon")
errors = []


def require(condition, reason):
    if not condition:
        errors.append(reason)


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as exc:
        errors.append(f"Invalid or missing JSON: {path}: {exc}")
        return {}


meta = read_json(RESOURCE / "fabric.mod.json")
require(meta.get("id") == "illager_armada", "Wrong Fabric mod ID")
require(meta.get("environment") == "*", "Mod must load on server and client")
require(meta.get("depends", {}).get("minecraft") == "1.20.1", "Wrong Minecraft compatibility")
require(meta.get("depends", {}).get("smallships") == ">=2.0.2", "Wrong Small Ships dependency")
require("illager_armada.mixins.json" in meta.get("mixins", []), "Mixin configuration absent")

mixins = read_json(RESOURCE / "illager_armada.mixins.json")
require("PirateShipAccessMixin" in mixins.get("mixins", []), "Missing server ship mixin")
require("PirateShipRendererLodMixin" in mixins.get("client", []),
        "Missing client distant-cannon LOD mixin")

props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
version_match = re.search(r"(?m)^mod_version=(\S+)$", props)
require(version_match is not None, "Missing Gradle mod_version")
if version_match:
    version = version_match.group(1)
    require(re.fullmatch(r"1\.0\.1(?:\.1(?:-alpha\.[1-9][0-9]*)?)?", version) is not None,
            f"Unexpected release version {version!r}")
    require(meta.get("version") == "${version}",
            "Fabric mod metadata must expand the Gradle release version")

settings = (JAVA / "PirateSettings.java").read_text(encoding="utf-8")
require("public double naturalSpawnChance = 0.20;" in settings,
        "Natural spawn probability must be 20 percent")
require("public int encounterCheckTicks = 2400;" in settings,
        "Spawn checks must remain 2 minutes apart")
require("public int encounterCooldownTicks = 2400;" in settings,
        "Post-encounter cooldown must remain 2 minutes")
require("public float hostileHullHealthMultiplier = 1.10f;" in settings,
        "Public health default must be independent of any pack overrides")
require("Math.max(0.25f" in settings, "Must support custom pirate nerf multipliers")
require("public int configVersion = 1;" in settings,
        "Wrong release config schema version")
require("unlimitedTestAmmo" not in settings,
        "Old dev unlimited ammo config must be removed")
require("assaultBoatsEnabled" not in settings,
        "Retired assault boat switch must be removed")

commands = (JAVA / "IllagerArmada.java").read_text(encoding="utf-8")
require('literal("firetest")' not in commands,
        "Debug cannon test command must not be in release builds")
for name in ("encounter", "fleet", "clearall", "perf", "status"):
    require(f'literal("{name}")' in commands,
            f"Expected operator command missing: {name}")

for hull in HULLS:
    path = RESOURCE / "data" / "illager_armada" / "pirate_cargo" / f"{hull}.json"
    cargo = read_json(path)
    pools = cargo.get("pools", [])
    require(isinstance(pools, list) and len(pools) > 0,
            f"{hull}: missing cargo pools")
    if not isinstance(pools, list):
        continue
    for index, pool in enumerate(pools):
        if not isinstance(pool, dict):
            errors.append(f"{hull} pool {index}: not a JSON object")
            continue
        minimum = pool.get("rolls_min", 1)
        maximum = pool.get("rolls_max", minimum)
        require(isinstance(minimum, int) and isinstance(maximum, int) and
                0 <= minimum <= maximum <= 100,
                f"{hull} pool {index}: bad roll bounds")
        entries = pool.get("entries", [])
        require(isinstance(entries, list) and len(entries) > 0,
                f"{hull} pool {index}: no entries")
        if not isinstance(entries, list):
            continue
        positive_weight = False
        for item in entries:
            if not isinstance(item, dict):
                errors.append(f"{hull}: item is not an object")
                continue
            identifier = item.get("item", "")
            require(isinstance(identifier, str) and
                    re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_/.-]+", identifier) is not None,
                    f"{hull}: invalid item ID {identifier!r}")
            low = item.get("min", 1)
            high = item.get("max", low)
            weight = item.get("weight", 1)
            require(isinstance(low, int) and isinstance(high, int) and
                    1 <= low <= high <= 256,
                    f"{hull}: invalid entry quantities for {identifier}")
            require(isinstance(weight, int) and weight >= 0,
                    f"{hull}: negative/invalid weight for {identifier}")
            positive_weight |= isinstance(weight, int) and weight > 0
        require(positive_weight, f"{hull} pool {index}: no selectable entries")

for path in (
    ROOT / "README.md",
    ROOT / "CHANGELOG.md",
    ROOT / "docs" / "CONFIGURATION.md",
    ROOT / "docs" / "INSTALLATION.md",
    ROOT / "docs" / "LOOT-DATAPACKS.md",
    ROOT / "docs" / "RELEASE-CHECKLIST.md",
):
    require(path.exists() and path.stat().st_size > 300,
            f"Missing required release documentation: {path}")

for removed in ("PirateBrain", "PirateBattle", "PirateV5", "PirateV6Combat", "PirateV5Boats"):
    require(not (JAVA / f"{removed}.java").exists(), f"Obsolete class still present: {removed}")

for required_class in (
    "PirateShips", "PirateCrew", "PirateFleet", "PirateCombat",
    "PirateGunnery", "PirateBossBars", "PirateShipRoster",
):
    require((JAVA / f"{required_class}.java").exists(),
            f"Missing unified 1.0 source class: {required_class}")

for source in JAVA.rglob("*.java"):
    code = source.read_text(encoding="utf-8")
    require(re.search(r"\\bia_v[0-9]+_", code) is None,
            f"Versioned development entity tag remains in {source.name}")
    require("PirateV5" not in code and "PirateV6" not in code,
            f"Obsolete prototype class name remains in {source.name}")
    require("PirateBrain" not in code,
            f"Old Caravel controller reference remains in {source.name}")

gunnery = (JAVA / "PirateGunnery.java").read_text(encoding="utf-8")
require('private static final String ACCURACY_TAG="ia_accuracy_";' in gunnery,
        "Missing gunner accuracy tag prefix")
require("tag.substring(ACCURACY_TAG.length())" in gunnery,
        "Gunner accuracy must parse after the exact tag prefix")
require("tag.substring(15)" not in gunnery,
        "Hard-coded gunner accuracy index can crash world ticks")
require("tag.substring(\"ia_conf_\".length())" in (JAVA / "PirateTactics.java").read_text(encoding="utf-8"),
        "Captain confidence parsing should use its tag prefix length")

require("legacy" not in settings.lower() and "migrat" not in settings.lower(),
        "First-release config still contains private prerelease migration code")

require(not (ROOT / "pota").exists(),
        "Public mod repository still contains a modpack-specific config folder")

if errors:
    print(f"RELEASE VALIDATION FAILED ({len(errors)} findings)")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("Release metadata, public defaults, loot manifests and documentation: PASS")
print(f"Validated {len(HULLS)} cargo manifests; this is source/static validation only.")
