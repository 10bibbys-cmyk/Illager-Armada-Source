package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.entity.ship.abilities.Seatable;
import com.talhanation.smallships.world.entity.ship.seat.ShipSeat;
import com.talhanation.smallships.world.entity.ship.seat.SeatType;
import com.talhanation.smallships.world.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Physical, finite crew. Each promoted first mate can succeed a captain only once. */
public final class PirateCrew {
    public static final String CAPTAIN="ia_captain", MATE="ia_first_mate";
    public static final String GUNNER="ia_gunner", RESERVE="ia_reserve", ENGINEER="ia_engineer";
    public static final String PROMOTED="ia_succession_used";
    private PirateCrew() {}

    public static void install(ServerWorld world,Ship ship,PirateShipRoster.Hull hull) {
        Seatable seats=(Seatable)ship;
        Random rng=new Random(ship.getUuid().getMostSignificantBits()^0x55BA7E62L);
        if(ship instanceof Cannonable battery) installCannons(ship,battery,hull,rng);

        ShipSeat driver=seats.getSeats().stream()
            .filter(s->s.type()==SeatType.DRIVER).findFirst().orElse(null);
        if(driver==null)throw new IllegalStateException("No native driver seat on "+hull);

        if(spawn(world,ship,driver,CAPTAIN)==null)
            throw new IllegalStateException("Failed to mount captain");

        List<ShipSeat> passengers=new ArrayList<>();
        for(ShipSeat seat:seats.getSeats()) {
            if(seat.type()==SeatType.PASSENGER && seats.isSeatFree(seat.id()) &&
               !seats.isSeatBlocked(seat))passengers.add(seat);
        }
        passengers.sort((a,b)->Double.compare(
            a.getWorldPosition(ship).squaredDistanceTo(driver.getWorldPosition(ship)),
            b.getWorldPosition(ship).squaredDistanceTo(driver.getWorldPosition(ship))));
        // Always reserve the closest viable passenger position for a unique mate.
        if(!passengers.isEmpty())spawn(world,ship,passengers.remove(0),MATE);
        // Dedicated engineer takes the next closest deck seat. Never gunner or captain.
        if(!passengers.isEmpty())spawn(world,ship,passengers.remove(0),ENGINEER);

        if(ship instanceof Cannonable battery) {
            for(ShipSeat seat:seats.getSeats()){
                if(seat.type()!=SeatType.GUNNER || !seats.isSeatFree(seat.id()) ||
                   seats.isSeatBlocked(seat))continue;
                if(!battery.isCannonInSlot(seat.mappedCannonSlot()))continue;
                PillagerEntity gunner=spawn(world,ship,seat,GUNNER);
                if(gunner!=null)gunner.addCommandTag("ia_gun_slot_"+seat.mappedCannonSlot());
            }
        }
        // Fill only real passenger stations. No invented deck mobs / entity spam.
        // Spawn only physical deck reserves; no off-ship support mobs.
        int reserveCount=Math.min(passengers.size(),Math.max(2,hull.reserveGoal));
        for(int i=0;i<reserveCount;i++)spawn(world,ship,passengers.get(i),RESERVE);
    }

    private static void installCannons(Ship ship,Cannonable battery,
                                       PirateShipRoster.Hull hull,Random rng) {
        int total=battery.getTotalCannonSlots();
        int maximum=Math.min(total,hull.cannons);
        if(maximum==0)return;
        int desired=hull==PirateShipRoster.Hull.GALLEON ? maximum :
            Math.min(maximum,Math.max(2,maximum-rng.nextInt(Math.min(3,maximum))));
        List<Integer> left=new ArrayList<>(),right=new ArrayList<>();
        for(int i=0;i<total;i++) {
            if(battery.getCannonPosition(i).isRightSided)right.add(i);
            else left.add(i);
        }
        java.util.Collections.shuffle(left,rng);java.util.Collections.shuffle(right,rng);
        if(!left.isEmpty()&&!right.isEmpty()) {
            battery.setCannonInSlot(left.remove(0),true);
            battery.setCannonInSlot(right.remove(0),true);
            desired-=2;
        }
        while(desired-->0 && (!left.isEmpty()||!right.isEmpty())){
            List<Integer> side=(left.isEmpty()?right:
                right.isEmpty()?left:rng.nextBoolean()?left:right);
            battery.setCannonInSlot(side.remove(0),true);
        }
    }

    private static PillagerEntity spawn(ServerWorld world,Ship ship,ShipSeat seat,String role) {
        PillagerEntity entity=EntityType.PILLAGER.create(world);
        if(entity==null)return null;
        entity.refreshPositionAndAngles(ship.getX(),ship.getY()+2,ship.getZ(),ship.getYaw(),0);
        entity.setAiDisabled(true);
        entity.setPersistent();
        entity.addCommandTag(IllagerArmada.ILLAGER);
        entity.addCommandTag("pirate_crew");
        entity.addCommandTag(role);
        entity.addCommandTag("ia_parent_"+ship.getUuidAsString());
        UUID fleet=PirateFleet.group(ship);
        if(fleet!=null)entity.addCommandTag("ia_crew_fleet_"+fleet);
        if(role.equals(GUNNER)||role.equals(RESERVE))
            entity.addCommandTag("ia_accuracy_"+(50+world.random.nextInt(46)));
        if(role.equals(RESERVE)||role.equals(MATE))
            entity.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.CROSSBOW));
        if(!world.spawnEntity(entity) || !entity.startRiding(ship,true)){
            entity.discard();return null;
        }
        ((Seatable)ship).assignSeat(entity,seat.id());
        return entity;
    }

    public static PillagerEntity occupant(Ship ship,String role) {
        for(Entity entity:ship.getPassengerList()) {
            if(entity instanceof PillagerEntity pillager && pillager.isAlive() &&
               pillager.getCommandTags().contains(role))return pillager;
        }
        return null;
    }
    public static List<PillagerEntity> occupants(Ship ship,String role) {
        List<PillagerEntity> result=new ArrayList<>();
        for(Entity entity:ship.getPassengerList()) {
            if(entity instanceof PillagerEntity pillager && pillager.isAlive() &&
               pillager.getCommandTags().contains(role))result.add(pillager);
        }
        return result;
    }
    public static int alive(Ship ship,String role){return occupants(ship,role).size();}

    public static PillagerEntity gunnerFor(Ship ship,int cannonSlot) {
        if(!(ship instanceof Cannonable battery))return null;
        List<PillagerEntity> gunners=occupants(ship,GUNNER);
        if(gunners.isEmpty())return null;
        // Every Galleon barrel is controlled by ONE physical seat. Missing
        // operators silence their exact group rather than redistributing all
        // barrels to remaining crew. Reserves restore that seat when available.
        if(PirateShipRoster.hullOf(ship)==PirateShipRoster.Hull.GALLEON) {
            boolean right=battery.getCannonPosition(cannonSlot).isRightSided;
            List<ShipSeat> stations=new ArrayList<>();
            for(ShipSeat seat:((Seatable)ship).getSeats()) {
                if(seat.type()!=SeatType.GUNNER)continue;
                if(battery.getCannonPosition(seat.mappedCannonSlot()).isRightSided==right)
                    stations.add(seat);
            }
            stations.sort(java.util.Comparator.comparingInt(ShipSeat::id));
            if(stations.isEmpty())return null;
            int ordinal=0;
            for(int i=0;i<cannonSlot;i++)
                if(battery.getCannonPosition(i).isRightSided==right)ordinal++;
            ShipSeat assigned=stations.get(ordinal%stations.size());
            Entity operator=((Seatable)ship).getSeatOccupant(assigned.id());
            if(operator instanceof PillagerEntity pillager && pillager.isAlive() &&
               pillager.getCommandTags().contains(GUNNER) &&
               stationSlot(pillager)==assigned.mappedCannonSlot())
                return pillager;
            return null;
        }
        for(PillagerEntity gunner:gunners)
            if(stationSlot(gunner)==cannonSlot)return gunner;
        return null;
    }
    private static int stationSlot(PillagerEntity pillager) {
        for(String tag:pillager.getCommandTags()) {
            if(!tag.startsWith("ia_gun_slot_"))continue;
            try{return Integer.parseInt(tag.substring("ia_gun_slot_".length()));}
            catch(NumberFormatException ignored){}
        }
        return -1;
    }

    /** Called at most once every ten ticks; no replacement crew are spawned. */
    public static boolean maintain(Ship ship) {
        Seatable seats=(Seatable)ship;
        PillagerEntity captain=occupant(ship,CAPTAIN);
        if(captain==null && !ship.getCommandTags().contains(PROMOTED)){
            PillagerEntity mate=occupant(ship,MATE);
            if(mate!=null) {
                mate.removeScoreboardTag(MATE);
                mate.addCommandTag(CAPTAIN);
                ship.addCommandTag(PROMOTED);
                ShipSeat helm=seats.getSeats().stream()
                    .filter(s->s.type()==SeatType.DRIVER).findFirst().orElse(null);
                if(helm!=null){seats.freeSeatOf(mate);seats.assignSeat(mate,helm.id());}
                captain=mate;
            }
        }
        if(captain==null)return false;
        // Repair the driver assignment on reload or after succession.
        // This runs at the throttled crew-maintenance interval, not every tick.
        if(ship.getHelmsman()!=captain){
            for(ShipSeat seat:seats.getSeats()){
                if(seat.type()==SeatType.DRIVER){
                    seats.assignSeat(captain,seat.id());
                    break;
                }
            }
        }
        if(ship instanceof Cannonable battery){
            for(ShipSeat station:seats.getSeats()){
                if(station.type()!=SeatType.GUNNER || !seats.isSeatFree(station.id()) ||
                   seats.isSeatBlocked(station) ||
                   !battery.isCannonInSlot(station.mappedCannonSlot()))continue;
                List<PillagerEntity> reserves=occupants(ship,RESERVE);
                if(reserves.isEmpty())break;
                PillagerEntity next=reserves.get(0);
                seats.freeSeatOf(next);
                next.removeScoreboardTag(RESERVE);
                next.addCommandTag(GUNNER);
                next.addCommandTag("ia_gun_slot_"+station.mappedCannonSlot());
                seats.assignSeat(next,station.id());
            }
        }
        return true;
    }

    /** Prioritise damaged cannon stations, then shipboard reserve shortages. */
    public static Ship reinforcementTarget(ServerWorld world,Ship carrier){
        if(alive(carrier,RESERVE)<4)return null;
        UUID fleet=PirateFleet.group(carrier);
        if(fleet==null)return null;
        Ship best=null;
        double score=0;
        for(Ship candidate:PirateShips.loaded(world)){
            if(candidate==carrier || candidate.isRemoved() || !candidate.isAlive() ||
               candidate.isSinking() || candidate.isSunken() ||
               !fleet.equals(PirateFleet.group(candidate)) ||
               candidate.squaredDistanceTo(carrier)>135*135)continue;
            Seatable seats=(Seatable)candidate;
            boolean free=false;
            int emptyGuns=0;
            for(ShipSeat seat:seats.getSeats()){
                if(seat.type()==SeatType.PASSENGER &&
                   seats.isSeatFree(seat.id()))free=true;
                if(seat.type()==SeatType.GUNNER &&
                   candidate instanceof Cannonable battery &&
                   battery.isCannonInSlot(seat.mappedCannonSlot()) &&
                   seats.isSeatFree(seat.id()))emptyGuns++;
            }
            if(!free)continue;
            int spare=alive(candidate,RESERVE);
            if(emptyGuns==0 && spare>=2)continue;
            double value=emptyGuns*20+Math.max(0,2-spare)*7-
                Math.sqrt(candidate.squaredDistanceTo(carrier))*.06;
            if(value>score){score=value;best=candidate;}
        }
        return best;
    }

    /** Transfer one existing reserve; no extra entities and no officer/engineer cloning. */
    public static boolean reinforce(Ship carrier,Ship ally){
        if(alive(carrier,RESERVE)<4 ||
           !carrier.isAlive() || !ally.isAlive() ||
           carrier.squaredDistanceTo(ally)>16*16)return false;
        UUID fleet=PirateFleet.group(carrier);
        if(fleet==null || !fleet.equals(PirateFleet.group(ally)))return false;
        Seatable source=(Seatable)carrier,destination=(Seatable)ally;
        ShipSeat free=null;
        for(ShipSeat seat:destination.getSeats())
            if(seat.type()==SeatType.PASSENGER &&
               destination.isSeatFree(seat.id()) && !destination.isSeatBlocked(seat)){
                free=seat;break;
            }
        if(free==null)return false;
        PillagerEntity illager=occupant(carrier,RESERVE);
        if(illager==null)return false;
        ShipSeat origin=source.getSeatOf(illager);
        source.freeSeatOf(illager);
        illager.stopRiding();
        illager.refreshPositionAndAngles(ally.getX(),ally.getY()+2,ally.getZ(),ally.getYaw(),0);
        if(!illager.startRiding(ally,true)){
            illager.refreshPositionAndAngles(carrier.getX(),carrier.getY()+2,
                carrier.getZ(),carrier.getYaw(),0);
            if(illager.startRiding(carrier,true) && origin!=null)
                source.assignSeat(illager,origin.id());
            return false;
        }
        destination.assignSeat(illager,free.id());
        for(String tag:List.copyOf(illager.getCommandTags()))
            if(tag.startsWith("ia_parent_"))illager.removeScoreboardTag(tag);
        illager.addCommandTag("ia_parent_"+ally.getUuidAsString());
        // Their original skill tag persists if they later replace a gunner.
        return true;
    }

    public static List<PillagerEntity> boatCrew(Ship ship) {
        List<PillagerEntity> reserves=occupants(ship,RESERVE);
        if(reserves.size()<2)return List.of();
        return List.of(reserves.get(0),reserves.get(1));
    }

    public static void abandon(ServerWorld world,Ship ship,boolean administrative) {
        if(!ship.getCommandTags().add("ia_crew_abandoned"))return;
        List<PillagerEntity> all=new ArrayList<>();
        for(Entity passenger:List.copyOf(ship.getPassengerList()))
            if(passenger instanceof PillagerEntity p && p.isAlive() &&
               p.getCommandTags().contains(IllagerArmada.ILLAGER))all.add(p);
        int i=0;
        for(PillagerEntity p:all){
            p.stopRiding();
            if(administrative){p.discard();continue;}
            // Cannon operators and the captain may have been unarmed while
            // seated. Give each survivor a genuine crossbow before native
            // swimming/hostile AI resumes; no new Pillagers are generated.
            if(p.getMainHandStack().isEmpty())
                p.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.CROSSBOW));
            p.setAiDisabled(false);
            double angle=ship.getYaw()*Math.PI/180+2*Math.PI*(i++/(double)Math.max(1,all.size()));
            p.refreshPositionAndAngles(ship.getX()+Math.cos(angle)*8,
                Math.max(world.getSeaLevel()+.2,ship.getY()+.8),
                ship.getZ()+Math.sin(angle)*8,ship.getYaw(),0);
            p.setVelocity(Math.cos(angle)*.25,.25,Math.sin(angle)*.25);
            p.velocityModified=true;
            PirateSurvivors.register(world,p);
        }
    }
}
