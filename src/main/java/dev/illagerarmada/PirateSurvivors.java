package dev.illagerarmada;

import net.minecraft.entity.Entity;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps escaped illagers alive and hostile while players are nearby, but
 * cleans up persistent test mobs once a battle has been abandoned.
 * Only loaded survivors are tracked; no chunk loading or world entity scans.
 */
public final class PirateSurvivors {
    public static final String SURVIVOR="ia_ship_survivor";
    private static final String EXPIRE_PREFIX="ia_survivor_expire_";
    private static final Map<ServerWorld,Map<UUID,PillagerEntity>> LOADED=
        new IdentityHashMap<>();
    private static final Map<ServerWorld,Map<UUID,PillagerEntity>> CREW=
        new IdentityHashMap<>();
    private static final long MAX_IDLE_TICKS=6000; // five minutes
    private static final double RETAIN_RADIUS_SQ=80.0*80.0;
    private PirateSurvivors() {}

    public static void onLoad(Entity entity,ServerWorld world) {
        if(!(entity instanceof PillagerEntity pillager))return;
        if(pillager.getCommandTags().contains(SURVIVOR))
            LOADED.computeIfAbsent(world,w->new HashMap<>())
                .put(pillager.getUuid(),pillager);
        if(pillager.getCommandTags().contains(IllagerArmada.ILLAGER) &&
           pillager.getCommandTags().stream().anyMatch(t->t.startsWith("ia_parent_")))
            CREW.computeIfAbsent(world,w->new HashMap<>())
                .put(pillager.getUuid(),pillager);
    }

    public static void onUnload(Entity entity,ServerWorld world) {
        if(entity instanceof PillagerEntity) {
            Map<UUID,PillagerEntity> tracked=CREW.get(world);
            if(tracked!=null)tracked.remove(entity.getUuid());
        }
        if(entity.getCommandTags().contains(SURVIVOR)) {
            Map<UUID,PillagerEntity> entities=LOADED.get(world);
            if(entities!=null)entities.remove(entity.getUuid());
        }
    }

    public static void register(ServerWorld world,PillagerEntity illager) {
        illager.addCommandTag(SURVIVOR);
        illager.addCommandTag(EXPIRE_PREFIX+(world.getTime()+MAX_IDLE_TICKS));
        LOADED.computeIfAbsent(world,w->new HashMap<>())
            .put(illager.getUuid(),illager);
        CREW.computeIfAbsent(world,w->new HashMap<>())
            .put(illager.getUuid(),illager);
    }

    public static void tick(ServerWorld world) {
        if(world.getTime()%40!=0)return; // 2 seconds
        Map<UUID,PillagerEntity> crew=CREW.get(world);
        if(crew!=null)for(PillagerEntity illager:new ArrayList<>(crew.values())){
            if(!illager.isAlive() || illager.isRemoved()){
                crew.remove(illager.getUuid());continue;
            }
            if(illager.getCommandTags().contains(SURVIVOR))continue;
            // A passenger that falls overboard becomes a recoverable survivor.
            // Legacy launch boats are allowed to finish their own lifecycle.
            Entity vehicle=illager.getVehicle();
            if(vehicle instanceof com.talhanation.smallships.world.entity.ship.Ship ||
               vehicle instanceof net.minecraft.entity.vehicle.BoatEntity)continue;
            register(world,illager);
        }
        Map<UUID,PillagerEntity> loaded=LOADED.get(world);
        if(loaded==null || loaded.isEmpty())return;
        for(PillagerEntity illager:new ArrayList<>(loaded.values())) {
            if(!illager.isAlive() || illager.isRemoved()){
                loaded.remove(illager.getUuid());
                continue;
            }
            long expires=Long.MAX_VALUE;
            for(String tag:illager.getCommandTags()) {
                if(!tag.startsWith(EXPIRE_PREFIX))continue;
                try {expires=Long.parseLong(tag.substring(EXPIRE_PREFIX.length()));}
                catch(NumberFormatException ignored) {}
                break;
            }
            if(world.getTime()<expires)continue;
            boolean playerNearby=false;
            for(ServerPlayerEntity player:world.getPlayers()) {
                if(!player.isAlive() || player.isSpectator())continue;
                if(illager.squaredDistanceTo(player)<RETAIN_RADIUS_SQ) {
                    playerNearby=true;break;
                }
            }
            if(!playerNearby){
                illager.discard();
                loaded.remove(illager.getUuid());
            }
        }
    }


    /**
     * Opportunistic pickup, once per second per ship. Never pathfind towards
     * swimmers, never generate crew, and never search unloaded chunks.
     * Rescued illagers become ordinary reserves and can later man empty guns.
     */
    public static boolean rescue(ServerWorld world,
                                 com.talhanation.smallships.world.entity.ship.Ship ship){
        Map<UUID,PillagerEntity> list=LOADED.get(world);
        if(list==null||list.isEmpty())return false;
        com.talhanation.smallships.world.entity.ship.abilities.Seatable seats=
            (com.talhanation.smallships.world.entity.ship.abilities.Seatable)ship;
        com.talhanation.smallships.world.entity.ship.seat.ShipSeat slot=null;
        for(var seat:seats.getSeats()){
            if(seat.type()==com.talhanation.smallships.world.entity.ship.seat.SeatType.PASSENGER &&
               seats.isSeatFree(seat.id()) && !seats.isSeatBlocked(seat)){
                slot=seat;break;
            }
        }
        if(slot==null)return false;
        for(PillagerEntity illager:new ArrayList<>(list.values())){
            if(!illager.isAlive() || illager.isRemoved() || illager.hasVehicle() ||
               !illager.getCommandTags().contains(IllagerArmada.ILLAGER))continue;
            if(!ship.getBoundingBox().expand(3.0).intersects(
                illager.getBoundingBox()))continue;
            // Prevent an unrelated pirate vessel from kidnapping another crew.
            // Fleet survivors can join another vessel in their original fleet.
            UUID group=PirateFleet.group(ship);
            boolean sameFleet=false;
            for(String fleetTag:illager.getCommandTags())if(fleetTag.startsWith("ia_crew_fleet_"))
                sameFleet=group!=null && fleetTag.equals("ia_crew_fleet_"+group);
            for(String parent:illager.getCommandTags()) {
                if(!parent.startsWith("ia_parent_"))continue;
                try{
                    Ship previous=PirateShips.find(world,
                        UUID.fromString(parent.substring("ia_parent_".length())));
                    sameFleet=sameFleet || previous!=null && group!=null &&
                        group.equals(PirateFleet.group(previous));
                }catch(IllegalArgumentException ignored){}
            }
            if(!sameFleet && !illager.getCommandTags().contains(
                "ia_parent_"+ship.getUuidAsString()))continue;
            illager.stopRiding();
            illager.refreshPositionAndAngles(ship.getX(),ship.getY()+2,
                ship.getZ(),ship.getYaw(),0);
            illager.setAiDisabled(true);
            if(!illager.startRiding(ship,true)){
                illager.setAiDisabled(false);continue;
            }
            for(String tag:java.util.Set.copyOf(illager.getCommandTags()))
                if(tag.startsWith(EXPIRE_PREFIX) || tag.startsWith("ia_parent_") ||
                   tag.startsWith("ia_gun_slot_") ||
                   tag.startsWith("ia_crew_fleet_") ||
                   tag.equals(PirateCrew.CAPTAIN) ||
                   tag.equals(PirateCrew.MATE) ||
                   tag.equals(PirateCrew.GUNNER) ||
                   tag.equals(PirateCrew.ENGINEER) ||
                   tag.equals(SURVIVOR))
                    illager.removeScoreboardTag(tag);
            illager.addCommandTag("ia_parent_"+ship.getUuidAsString());
            if(group!=null)illager.addCommandTag("ia_crew_fleet_"+group);
            illager.addCommandTag(PirateCrew.RESERVE);
            if(illager.getMainHandStack().isEmpty())
                illager.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.CROSSBOW));
            seats.assignSeat(illager,slot.id());
            list.remove(illager.getUuid());
            return true;
        }
        return false;
    }

    public static int count(ServerWorld world) {
        Map<UUID,PillagerEntity> loaded=LOADED.get(world);
        return loaded==null?0:loaded.size();
    }

    public static void reset() { LOADED.clear();CREW.clear(); }
    /** Only tracked, already-loaded crew for this vessel. Never scan all entities. */
    public static void clearCrewFor(ServerWorld world,Ship ship){
        String parent="ia_parent_"+ship.getUuidAsString();
        Map<UUID,PillagerEntity> crew=CREW.get(world);
        if(crew==null)return;
        Map<UUID,PillagerEntity> survivors=LOADED.get(world);
        for(PillagerEntity illager:new ArrayList<>(crew.values())){
            if(!illager.getCommandTags().contains(parent))continue;
            illager.discard();
            crew.remove(illager.getUuid());
            if(survivors!=null)survivors.remove(illager.getUuid());
        }
    }

}
