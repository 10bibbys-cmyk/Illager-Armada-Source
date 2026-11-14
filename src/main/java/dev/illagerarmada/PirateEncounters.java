package dev.illagerarmada;

import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-vessel-grouped, bounded ocean encounter spawning.
 * No chunk loads, worldgen, arbitrary mob spawn events or orphan vessels.
 */
public final class PirateEncounters {
    public static final String NATURAL="ia_natural_encounter";
    private static final String COOLDOWN="ia_encounter_after_";
    private PirateEncounters() {}

    public static void tick(ServerWorld world) {
        PirateSettings cfg=PirateSettings.ACTIVE;
        if(!cfg.naturalSpawningEnabled || world.getRegistryKey()!=World.OVERWORLD) return;
        if(world.getTime()%cfg.encounterCheckTicks!=0)return;
        if(world.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL &&
            !cfg.allowPeacefulSpawns)return;
        Map<UUID, List<ServerPlayerEntity>> vessels=eligibleVessels(world);
        for(List<ServerPlayerEntity> group:vessels.values()){
            if(group.isEmpty())continue;
            Entity vessel=PirateTactics.vesselOf(world,group.get(0));
            if(vessel==null || !vessel.isAlive())continue;
            if(!allReady(group,world.getTime()))continue;
            if(world.random.nextDouble()>=cfg.naturalSpawnChance)continue;
            spawnNear(world,vessel,group,false,false);
        }
    }

    /** Force a safe ocean spawn for a crewed vessel, bypassing chance/cooldown. */
    public static String force(ServerWorld world,ServerPlayerEntity player) {
        if(world.getRegistryKey()!=World.OVERWORLD)return "Natural encounters are Overworld-only.";
        Entity vessel=PirateTactics.vesselOf(world,player);
        if(vessel==null)return "Board a boat or Small Ships vessel to test encounters.";
        List<ServerPlayerEntity> crew=new ArrayList<>();
        for(ServerPlayerEntity other:world.getPlayers())
            if(PirateTactics.vesselOf(world,other)==vessel)crew.add(other);
        return spawnNear(world,vessel,crew,true,false)
            ? "Spawned an ocean pirate encounter at a safe, loaded location."
            : "No valid encounter site. Check ocean biome, water depth, loaded chunks or existing pirate cap.";
    }

    public static String forceFleet(ServerWorld world,ServerPlayerEntity player) {
        if(world.getRegistryKey()!=World.OVERWORLD)return "Fleet testing is Overworld ocean-only.";
        Entity vessel=PirateTactics.vesselOf(world,player);
        if(vessel==null)return "Board an active sailing ship before creating a fleet.";
        List<ServerPlayerEntity> crew=new ArrayList<>();
        for(ServerPlayerEntity other:world.getPlayers())
            if(PirateTactics.vesselOf(world,other)==vessel)crew.add(other);
        return spawnNear(world,vessel,crew,true,true)
            ? "Spawned a four-ship fleet with a Galleon flagship."
            : "No valid fully-loaded deep-ocean fleet formation site or active-ship budget.";
    }

    private static Map<UUID,List<ServerPlayerEntity>> eligibleVessels(ServerWorld world) {
        Map<UUID,List<ServerPlayerEntity>> groups=new HashMap<>();
        for(ServerPlayerEntity player:world.getPlayers()){
            if(!player.isAlive() || player.isSpectator() || player.isCreative())continue;
            Entity vessel=PirateTactics.vesselOf(world,player);
            if(vessel==null || !vessel.isAlive())continue;
            if(vessel.getCommandTags().contains(IllagerArmada.SHIP))continue;
            groups.computeIfAbsent(vessel.getUuid(),unused->new ArrayList<>()).add(player);
        }
        return groups;
    }

    private static boolean allReady(List<ServerPlayerEntity> crew,long time) {
        for(ServerPlayerEntity player:crew){
            for(String tag:player.getCommandTags()){
                if(!tag.startsWith(COOLDOWN))continue;
                try { if(Long.parseLong(tag.substring(COOLDOWN.length()))>time)return false; }
                catch(NumberFormatException ignored){}
            }
        }
        return true;
    }

    private static void cooldown(List<ServerPlayerEntity> crew,long until) {
        for(ServerPlayerEntity player:crew) {
            for(String tag:List.copyOf(player.getCommandTags()))
                if(tag.startsWith(COOLDOWN))player.removeScoreboardTag(tag);
            player.addCommandTag(COOLDOWN+until);
        }
    }

    private static boolean spawnNear(ServerWorld world,Entity vessel,
                       List<ServerPlayerEntity> crew,boolean forced,boolean forceFleet) {
        PirateSettings cfg=PirateSettings.ACTIVE;
        int total=PirateShips.activeCount(world);
        int nearby=0;
        for(com.talhanation.smallships.world.entity.ship.Ship ship:PirateShips.loaded(world)) {
            if(ship.isSinking()||ship.isSunken())continue;
            if(ship.squaredDistanceTo(vessel)<
                (double)cfg.nearbyPirateRange*cfg.nearbyPirateRange)nearby++;
        }
        if(total>=cfg.maxPiratesPerDimension ||
            nearby>=cfg.maximumPiratesNearVessel)return false;

        BlockPos boat=BlockPos.ofFloored(vessel.getX(),world.getSeaLevel()-1,vessel.getZ());
        if(!world.isChunkLoaded(boat) || !world.getBiome(boat).isIn(BiomeTags.IS_OCEAN))
            return false;
        boolean fleet=forceFleet || cfg.fleetsEnabled &&
            world.random.nextDouble()<cfg.fleetEncounterChance &&
            total+4<=cfg.maxPiratesPerDimension;
        if(forceFleet && (!cfg.fleetsEnabled || total+4>cfg.maxPiratesPerDimension))
            return false;
        // Prefer the intended distant horizon. With limited simulation
        // distance, the ring may not be loaded; a final bounded pass searches
        // slightly closer (still at least 64 blocks away). No chunk loading.
        for(int attempt=0;attempt<48;attempt++){
            double radians=world.random.nextDouble()*Math.PI*2;
            int radius;
            if(attempt<32 || !cfg.loadedSpawnFallbackEnabled){
                radius=cfg.spawnMinDistance+
                    world.random.nextInt(cfg.spawnMaxDistance-cfg.spawnMinDistance+1);
            }else{
                int near=Math.min(cfg.spawnMinDistance,cfg.spawnFallbackMinDistance);
                int far=Math.max(near,cfg.spawnMinDistance);
                radius=near+world.random.nextInt(far-near+1);
            }
            int x=(int)Math.floor(vessel.getX()+Math.cos(radians)*radius);
            int z=(int)Math.floor(vessel.getZ()+Math.sin(radians)*radius);
            BlockPos pos=new BlockPos(x,world.getSeaLevel(),z);
            float yaw=(float)(world.random.nextDouble()*360-180);
            if(fleet && cfg.fleetsEnabled){
                if(safeWater(world,pos,Math.max(cfg.minimumWaterDepth,10))){
                    var flagship=PirateFleet.spawn(world,pos,yaw);
                    if(flagship!=null){
                        if(!forced){
                            UUID id=PirateFleet.group(flagship);
                            for(com.talhanation.smallships.world.entity.ship.Ship ally:
                                    PirateShips.loaded(world))
                                if(id!=null && id.equals(PirateFleet.group(ally)))
                                    ally.addCommandTag(NATURAL);
                        }
                        cooldown(crew,world.getTime()+cfg.encounterCooldownTicks);
                        PirateSignals.encounter(world,crew,flagship.getPos(),
                            "Galleon",true);
                        return true;
                    }
                }
                if(forceFleet)continue;
            }
            if(forceFleet)continue;
            PirateShipRoster.Hull hull=PirateShipRoster.Hull.choose(
                new java.util.Random(world.random.nextLong()));
            if(!safeWater(world,pos,Math.max(cfg.minimumWaterDepth,hull.depth)) ||
               !PirateShips.validWater(world,pos,hull))continue;
            var ship=PirateShips.spawn(world,pos,yaw,hull,
                PirateShipRoster.randomWood(new java.util.Random(world.random.nextLong())),
                null,null,null);
            if(ship==null)continue;
            ship.addCommandTag(NATURAL);
            cooldown(crew,world.getTime()+cfg.encounterCooldownTicks);
            PirateSignals.encounter(world,crew,ship.getPos(),
                hull.display,false);
            return true;
        }
        return false;
    }

    private static boolean safeWater(ServerWorld world,BlockPos surface,int depth) {
        // Check the entire hull footprint. Never call biome or fluid methods
        // for unloaded positions, and never generate chunks in navigation.
        for(int x=-5;x<=5;x+=5)for(int z=-5;z<=5;z+=5) {
            BlockPos center=surface.add(x,0,z);
            if(!world.isChunkLoaded(center))return false;
            if(!world.getBiome(center).isIn(BiomeTags.IS_OCEAN))return false;
            if(!world.getBlockState(center).getCollisionShape(world,center).isEmpty())
                return false;
            for(int up=1;up<=5;up++){
                BlockPos above=center.up(up);
                if(!world.isChunkLoaded(above) ||
                   !world.getBlockState(above).getCollisionShape(world,above).isEmpty())
                    return false;
            }
            for(int down=1;down<=depth;down++){
                BlockPos below=center.down(down);
                if(!world.isChunkLoaded(below) ||
                   !world.getFluidState(below).isOf(Fluids.WATER))return false;
            }
        }
        return true;
    }
}
