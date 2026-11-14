package dev.illagerarmada;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashMap;

/** Server-side defaults. Automatically written to config/illager_armada.json. */
public final class PirateSettings {
    public boolean naturalSpawningEnabled = true;
    public boolean allowPeacefulSpawns = false;
    public double naturalSpawnChance = 0.20;
    public int encounterCheckTicks = 2400;
    public int encounterCooldownTicks = 2400;
    public int spawnMinDistance = 80;
    public int spawnMaxDistance = 112;
    public boolean loadedSpawnFallbackEnabled = true;
    public int spawnFallbackMinDistance = 64;
    public int minimumWaterDepth = 5;
    public int nearbyPirateRange = 256;
    public int maxPiratesPerDimension = 4;
    public int maximumPiratesNearVessel = 1;
    public int naturalDespawnDistance = 192;
    public int naturalDespawnGraceTicks = 1200;
    public double gunnerAccuracyMultiplier = 0.75; // 25% less aim dispersion
    public boolean bossBarsEnabled = true;
    public boolean fleetsEnabled = true;
    public double fleetEncounterChance = 0.03;
    public int maxFleetsPerDimension = 1;
    // 1.0 = same HP as an identically upgraded Small Ships hull.
    // Values below 1.0 are supported for pack-specific pirate balancing.
    public float hostileHullHealthMultiplier = 1.10f;
    public boolean enemyShipUpgrades = true;
    public int ammoPerCannonMin = 24;
    public int ammoPerCannonMax = 40;
    public float ammoSalvageFraction = 0.25f;
    public double cargoRollMultiplier = 1.0;
    public boolean battleHornsEnabled = true;
    public boolean intimidationHornsEnabled = true;
    public int intimidationHornMinTicks = 260;
    public int intimidationHornMaxTicks = 440;
    public boolean openWaterRetreatEnabled = true;
    public boolean disciplinedGunneryEnabled = true;
    public boolean antiOrbitTrackingEnabled = true;
    public int antiOrbitTrackingRange = 60;
    public int cannonMaxFireRange = 65;
    public int chainShotMaxRange = 38;
    public float battleHornVolume = 1.8f;
    // Short signals for frequent manoeuvres; goat horns for major orders.
    // Individual resource IDs are configurable, "none" disables one signal.
    public Map<String,String> battleHornSounds = defaultHorns();
    public boolean fleetCollisionAvoidanceEnabled = true;
    public int cannonRenderLodDistance = 72; // 0 disables client distance culling
    public boolean distantCannonLodEnabled = true;

    public boolean encounterChatEnabled = true;
    public boolean encounterSoundEnabled = true;
    public boolean pirateSailsEnabled = true;
    public boolean intelligentAmmoEnabled = true;
    public boolean drakkarFleetOnly = true;
    public boolean drakkarReinforcementsEnabled = true;
    public int configVersion = 1;
    private static Map<String,String> defaultHorns(){
        Map<String,String> sounds=new LinkedHashMap<>();
        sounds.put("sighted","minecraft:item.goat_horn.sound.2");
        sounds.put("ram","minecraft:item.goat_horn.sound.5");
        sounds.put("retreat","minecraft:item.goat_horn.sound.3");
        sounds.put("repair","minecraft:block.bell.use");
        sounds.put("strafe","minecraft:block.note_block.didgeridoo");
        sounds.put("broadside","minecraft:block.note_block.didgeridoo");
        sounds.put("rally","minecraft:item.goat_horn.sound.6");
        sounds.put("intimidate","minecraft:item.goat_horn.sound.0"); // Ponder
        return sounds;
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static PirateSettings ACTIVE = new PirateSettings();

    private PirateSettings() {}

    public static void load() {
        Path path=FabricLoader.getInstance().getConfigDir().resolve("illager_armada.json");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path,GSON.toJson(ACTIVE),StandardCharsets.UTF_8);
                return;
            }
            String raw=Files.readString(path,StandardCharsets.UTF_8);
            PirateSettings parsed=GSON.fromJson(raw,PirateSettings.class);
            if(parsed!=null)ACTIVE=parsed;
        } catch (Exception ex) {
            System.err.println("[Illager Armada] Failed reading config: "+ex.getMessage());
        }
        ACTIVE.encounterCheckTicks=Math.max(200,ACTIVE.encounterCheckTicks);
        ACTIVE.encounterCooldownTicks=Math.max(0,ACTIVE.encounterCooldownTicks);
        ACTIVE.naturalSpawnChance=Math.max(0,Math.min(1,ACTIVE.naturalSpawnChance));
        ACTIVE.configVersion=1;
        ACTIVE.naturalDespawnGraceTicks=Math.max(200,
            Math.min(72000,ACTIVE.naturalDespawnGraceTicks));
        ACTIVE.spawnMinDistance=Math.max(48,ACTIVE.spawnMinDistance);
        ACTIVE.spawnMaxDistance=Math.max(ACTIVE.spawnMinDistance+8,ACTIVE.spawnMaxDistance);
        ACTIVE.spawnFallbackMinDistance=Math.max(48,
            Math.min(ACTIVE.spawnMinDistance,ACTIVE.spawnFallbackMinDistance));
        ACTIVE.minimumWaterDepth=Math.max(3,ACTIVE.minimumWaterDepth);
        ACTIVE.maxPiratesPerDimension=Math.max(1,ACTIVE.maxPiratesPerDimension);
        ACTIVE.maximumPiratesNearVessel=Math.max(1,ACTIVE.maximumPiratesNearVessel);
        ACTIVE.naturalDespawnDistance=Math.max(ACTIVE.spawnMaxDistance+32,ACTIVE.naturalDespawnDistance);
        ACTIVE.gunnerAccuracyMultiplier=Math.max(0.1,Math.min(3,ACTIVE.gunnerAccuracyMultiplier));
        ACTIVE.fleetEncounterChance=Math.max(0,Math.min(1,ACTIVE.fleetEncounterChance));
        ACTIVE.maxFleetsPerDimension=Math.max(0,ACTIVE.maxFleetsPerDimension);
        ACTIVE.hostileHullHealthMultiplier=Math.max(0.25f,
            Math.min(1.5f,ACTIVE.hostileHullHealthMultiplier));
        ACTIVE.ammoPerCannonMin=Math.max(1,Math.min(128,ACTIVE.ammoPerCannonMin));
        ACTIVE.ammoPerCannonMax=Math.max(ACTIVE.ammoPerCannonMin,
            Math.min(256,ACTIVE.ammoPerCannonMax));
        ACTIVE.ammoSalvageFraction=Math.max(0f,Math.min(1f,ACTIVE.ammoSalvageFraction));
        ACTIVE.cargoRollMultiplier=Math.max(0,Math.min(3,ACTIVE.cargoRollMultiplier));
        ACTIVE.cannonRenderLodDistance=Math.max(0,Math.min(240,ACTIVE.cannonRenderLodDistance));
        ACTIVE.battleHornVolume=Math.max(0f,Math.min(4f,ACTIVE.battleHornVolume));
        ACTIVE.intimidationHornMinTicks=Math.max(120,
            Math.min(2400,ACTIVE.intimidationHornMinTicks));
        ACTIVE.intimidationHornMaxTicks=Math.max(ACTIVE.intimidationHornMinTicks,
            Math.min(3600,ACTIVE.intimidationHornMaxTicks));
        ACTIVE.cannonMaxFireRange=Math.max(25,Math.min(85,ACTIVE.cannonMaxFireRange));
        ACTIVE.antiOrbitTrackingRange=Math.max(18,
            Math.min(ACTIVE.cannonMaxFireRange,ACTIVE.antiOrbitTrackingRange));
        ACTIVE.chainShotMaxRange=Math.max(18,
            Math.min(50,ACTIVE.chainShotMaxRange));
        if(ACTIVE.battleHornSounds==null)ACTIVE.battleHornSounds=defaultHorns();
        for(Map.Entry<String,String> entry:defaultHorns().entrySet())
            ACTIVE.battleHornSounds.putIfAbsent(entry.getKey(),entry.getValue());
        // Normalize the current configuration and keep the user's settings.
        try {
            String existing=Files.readString(path,StandardCharsets.UTF_8);
            String merged=GSON.toJson(ACTIVE);
            if(!existing.equals(merged))
                Files.writeString(path,merged,StandardCharsets.UTF_8);
        } catch(Exception ex) {
            System.err.println("[Illager Armada] Could not update config: "+ex.getMessage());
        }
    }
}
