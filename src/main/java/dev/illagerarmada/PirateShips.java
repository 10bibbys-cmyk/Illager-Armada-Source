package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.*;
import com.talhanation.smallships.world.entity.ship.ShipUpgrade;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.entity.ship.abilities.Seatable;
import com.talhanation.smallships.world.entity.ship.seat.SeatType;
import com.talhanation.smallships.world.item.CannonBallItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Naval controller for all seven Small Ships hulls; no chunk loads.
 * One update every 5 ticks, targets/crew every 20, health bars every 20,
 * no global entity scans per tick or on-demand creature creation.
 */
public final class PirateShips {
    public static final String TAG="ia_controller";
    private static final String RAM_USED="ia_ram_used";
    private static final String RAM_TARGET="ia_ram_target_";
    private static final Map<ServerWorld,Map<UUID,Ship>> LOADED=
        new IdentityHashMap<>();
    private static final Map<UUID,Memory> MEMORY=new HashMap<>();
    private static final Map<ServerWorld,Perf> PERF=new IdentityHashMap<>();
    private PirateShips(){}

    private static final class Memory {
        final PirateNavigation.Memory nav=new PirateNavigation.Memory();
        PirateTactics.Target target;
        UUID opponent;
        long targetAt,crewAt,barAt,lastShot,evadeUntil,ramUntil;
        UUID ramOpponent;
        long unmannedSince,outOfRangeSince,patrolUntil;
        Vec3d patrolTarget,prevTarget,targetVelocity=Vec3d.ZERO;
        long sampleAt,seenAt;
        boolean recalling;
        int observedShots,oldCrew;
        UUID reinforcement;
        long reinforcementAt,nextTransferAt;
        float oldDamage;
        String mode="patrol";
        int side=1;
    }
    private static final class Perf {
        long since,total,max,calls,previousTotal,previousMax,previousCalls;
    }

    public static void onLoad(Entity entity,ServerWorld world){
        if(entity instanceof Ship ship && ship.getCommandTags().contains(TAG))
            LOADED.computeIfAbsent(world,w->new HashMap<>()).put(ship.getUuid(),ship);
    }
    public static void onUnload(Entity entity,ServerWorld world){
        if(!(entity instanceof Ship ship) || !ship.getCommandTags().contains(TAG))return;
        Map<UUID,Ship> ships=LOADED.get(world);
        if(ships!=null)ships.remove(ship.getUuid());
        MEMORY.remove(ship.getUuid());
        PirateGunnery.forget(ship);
        PirateCombat.forget(ship);
        PirateSignals.forget(ship);
        PirateBossBars.remove(ship);
    }
    public static void reset(){
        LOADED.clear();MEMORY.clear();PERF.clear();
        PirateBossBars.reset();PirateCombat.reset();
        PirateSignals.reset();
        PirateGunnery.reset();
    }
    public static List<Ship> loaded(ServerWorld world){
        Map<UUID,Ship> ships=LOADED.get(world);
        return ships==null?List.of():List.copyOf(ships.values());
    }
    public static Ship find(ServerWorld world,UUID id){
        Map<UUID,Ship> ships=LOADED.get(world);
        return ships==null?null:ships.get(id);
    }
    public static int activeCount(ServerWorld world){
        int total=0;
        for(Ship ship:loaded(world))
            if(ship.isAlive()&&!ship.isSinking()&&!ship.isSunken())total++;
        return total;
    }

    public static boolean isFlagship(Ship ship){
        return PirateFleet.isFlagship(ship);
    }
    public static boolean engaged(Ship ship){
        if(!ship.getCommandTags().contains("ia_engaged"))return false;
        Memory m=MEMORY.get(ship.getUuid());
        return ship.isSinking() ||
            (m!=null && m.target!=null && m.target.vessel().isAlive());
    }
    public static boolean recallBoats(Ship ship){
        if(ship.isSinking()||ship.isSunken())return true;
        Memory m=MEMORY.get(ship.getUuid());
        return m!=null && m.recalling;
    }

    public static Ship spawn(ServerWorld world,BlockPos center,float yaw,
                             PirateShipRoster.Hull hull,BoatEntity.Type wood,
                             UUID fleet,String role,UUID leader) {
        if(hull==PirateShipRoster.Hull.GALLEON && fleet==null)return null;
        if(activeCount(world)>=
            PirateSettings.ACTIVE.maxPiratesPerDimension)return null;
        if(!validWater(world,center,hull))return null;
        Ship ship=PirateShipRoster.summon(world,hull,
            center.getX()+.5,center.getY(),center.getZ()+.5,wood,yaw);
        if(PirateSettings.ACTIVE.enemyShipUpgrades)
            for(ShipUpgrade upgrade:ShipUpgrade.values())upgrade.setInstalled(ship,true);
        ship.addCommandTag(IllagerArmada.SHIP);
        ship.addCommandTag(TAG);
        ship.addCommandTag("ia_mode_auto");
        ship.addCommandTag("ia_last_seen_"+world.getTime());
        ship.addCommandTag("ia_hull_"+hull.name().toLowerCase(java.util.Locale.ROOT));
        ship.addCommandTag("ia_personality_"+Math.floorMod(ship.getUuid().hashCode(),4));
        if(fleet!=null) {
            ship.addCommandTag(PirateFleet.FLEET+fleet);
            ship.addCommandTag(role);
            if(leader!=null)ship.addCommandTag(PirateFleet.LEADER+leader);
        }
        PirateTactics.confidence(ship);
        if(!world.spawnEntity(ship))return null;
        try{
            PirateCrew.install(world,ship,hull);
            PirateVisuals.apply(ship,hull);
            PirateCargo.provision(world,ship,hull);
        } catch(RuntimeException ex){
            System.err.println("[Illager Armada] Failed equipping "+hull+": "+ex.getMessage());
            clear(world,ship,true);
            return null;
        }
        return ship;
    }

    public static boolean validWater(ServerWorld world,BlockPos center,
                                      PirateShipRoster.Hull hull) {
        int half=hull.clearance;
        // All checks stay inside already-loaded chunks. No new terrain.
        int[] offsets={-half,0,half};
        for(int x:offsets)for(int z:offsets){
            BlockPos origin=center.add(x,0,z);
            if(!world.isChunkLoaded(origin))return false;
            for(int depth=1;depth<=hull.depth;depth++){
                BlockPos water=origin.down(depth);
                if(!world.isChunkLoaded(water) ||
                   !world.getFluidState(water).isOf(Fluids.WATER))return false;
            }
            for(int height=0;height<=7;height++){
                BlockPos above=origin.up(height);
                if(!world.isChunkLoaded(above) ||
                   !world.getBlockState(above).getCollisionShape(world,above).isEmpty())
                    return false;
            }
        }
        return true;
    }

    public static void tick(ServerWorld world){
        if(world.getTime()%5!=0)return;
        List<Ship> ships=loaded(world);
        if(ships.isEmpty())return; // absolutely no AI when no pirates are loaded
        long started=System.nanoTime();
        for(Ship ship:ships)if(!ship.isRemoved() && ship.isAlive())update(world,ship);
        Perf perf=PERF.computeIfAbsent(world,k->new Perf());
        if(perf.since==0)perf.since=world.getTime();
        long elapsed=System.nanoTime()-started;
        perf.total+=elapsed;perf.calls++;perf.max=Math.max(perf.max,elapsed);
        if(world.getTime()-perf.since>=400){
            perf.previousTotal=perf.total;perf.previousMax=perf.max;
            perf.previousCalls=perf.calls;
            perf.total=perf.max=perf.calls=0;
            perf.since=world.getTime();
        }
    }
    public static String performance(ServerWorld world){
        Perf p=PERF.get(world);
        if(p==null)return "Illager Armada: no loaded pirate ships or AI samples.";
        long calls=p.previousCalls>0?p.previousCalls:p.calls;
        long elapsed=p.previousCalls>0?p.previousTotal:p.total;
        long max=p.previousCalls>0?p.previousMax:p.max;
        return String.format(java.util.Locale.ROOT,
            "Illager Armada active=%d, AI=%.3f ms/5t, max=%.3f ms; excludes native Small Ships physics",
            activeCount(world),
            elapsed/Math.max(1.0,calls)/1e6,max/1e6);
    }

    private static void update(ServerWorld world,Ship ship){
        Memory m=MEMORY.computeIfAbsent(ship.getUuid(),id->new Memory());
        long now=world.getTime();
        // Unloaded chunks never tick. When an old encounter is loaded again,
        // retire it out of sight instead of resurrecting abandoned pirates.
        if(m.seenAt==0 && ship.getCommandTags().contains(PirateEncounters.NATURAL)){
            long lastSeen=now;
            for(String tag:ship.getCommandTags())if(tag.startsWith("ia_last_seen_")){
                try{lastSeen=Long.parseLong(
                    tag.substring("ia_last_seen_".length()));}
                catch(NumberFormatException ignored){}
                break;
            }
            if(now-lastSeen>PirateSettings.ACTIVE.naturalDespawnGraceTicks*2L){
                boolean near=false;
                for(ServerPlayerEntity player:world.getPlayers())
                    if(player.isAlive() && !player.isSpectator() &&
                        player.squaredDistanceTo(ship)<96*96){near=true;break;}
                if(!near){clear(world,ship,true);return;}
            }
        }
        m.seenAt=now;
        if(!ship.isSinking() && !ship.isSunken()){
            if(!ship.getCommandTags().contains("ia_provisioned"))
                PirateCargo.provision(world,ship,PirateShipRoster.hullOf(ship));
            if(!ship.getCommandTags().contains("ia_colours"))
                PirateVisuals.apply(ship,PirateShipRoster.hullOf(ship));
        }
        if(ship.isSinking()||ship.isSunken()){
            if(ship.isSinking()) {
                PirateNavigation.stop(ship);
                prepareWreck((ContainerShip)ship);
                PirateCrew.abandon(world,ship,ship.getCommandTags().contains("ia_no_loot"));
            }
            return; // Native Small Ships owns wreck/despawn timers.
        }
        if(ship.getCommandTags().contains("ia_admin_scuttle")){
            startSinking(world,ship,true);return;
        }
        for(String tag:ship.getCommandTags()){
            if(!tag.startsWith("ia_admin_scuttle_at_"))continue;
            try{
                if(now>=Long.parseLong(tag.substring("ia_admin_scuttle_at_".length()))){
                    ship.removeScoreboardTag(tag);
                    startSinking(world,ship,true);return;
                }
            } catch(NumberFormatException ignored){}
            break;
        }
        boolean near=false;
        for(ServerPlayerEntity player:world.getPlayers()){
            if(player.isSpectator())continue;
            if(player.squaredDistanceTo(ship)<
               Math.pow(PirateSettings.ACTIVE.naturalDespawnDistance,2)){
                near=true;break;
            }
        }
        if(!near){
            if(m.outOfRangeSince==0)m.outOfRangeSince=now;
            PirateNavigation.stop(ship);
            if(now-m.outOfRangeSince>PirateSettings.ACTIVE.naturalDespawnGraceTicks)
                clear(world,ship,true);
            return;
        }
        m.outOfRangeSince=0;
        // A persisted, low-frequency encounter clock permits safe post-reload cleanup.
        if(now%1200==0){
            for(String tag:List.copyOf(ship.getCommandTags()))
                if(tag.startsWith("ia_last_seen_"))ship.removeScoreboardTag(tag);
            ship.addCommandTag("ia_last_seen_"+now);
        }

        if(now>=m.crewAt){
            m.crewAt=now+20;
            PirateSurvivors.rescue(world,ship);
            if(!PirateCrew.maintain(ship)){
                if(m.unmannedSince==0)m.unmannedSince=now;
                if(now-m.unmannedSince>600)startSinking(world,ship,false);
                else PirateNavigation.stop(ship);
                return;
            }
            m.unmannedSince=0;
            float damage=ship.getDamageWobbleStrength();
            int crew=ship.getPassengerList().size();
            // Keep a firing position under routine fire. Reposition only
            // after a major hull hit or multiple simultaneous casualties.
            if(m.oldDamage>0 &&
                  damage>m.oldDamage+Math.max(6,ship.getAttributes().maxHealth*.10f) ||
               m.oldCrew>0 && crew<=m.oldCrew-2) {
                m.evadeUntil=now+40;
            }
            m.oldDamage=damage;m.oldCrew=crew;
        }
        if(m.unmannedSince!=0)return;

        if(now>=m.targetAt || m.target==null ||
           !m.target.vessel().isAlive() || m.target.player().isRemoved()){
            m.target=PirateTactics.findTarget(world,ship,100);
            m.targetAt=now+20;
        }
        PirateTactics.Target target=m.target;
        PirateCombat.Directive command=PirateCombat.tick(world,ship,target);
        if(command!=null){
            m.mode=command.mode();
            if(command.stop())PirateNavigation.stop(ship);
            else if(command.mode().equals("full retreat") ||
                    command.mode().equals("repair withdrawal"))
                PirateNavigation.retreat(world,ship,
                    target==null?null:target.vessel(),command.sails(),m.nav);
            else PirateNavigation.sail(world,ship,command.destination(),command.sails(),m.nav);
            return;
        }
        if(target==null){
            m.opponent=null;
            Ship leader=PirateFleet.flagship(world,ship);
            if(leader!=null && leader!=ship && leader.isAlive() &&
               !leader.isSinking() && !leader.isSunken()){
                // Attackers and guards return to the flagship whenever their
                // target escapes beyond acquisition range. They may NOT keep
                // chasing an unloaded or unseen player indefinitely.
                m.recalling=true;m.mode="fleet regroup";
                Vec3d direction=fromYaw(leader.getYaw());
                Vec3d right=new Vec3d(direction.z,0,-direction.x);
                Vec3d place=leader.getPos().add(right.multiply(
                    PirateFleet.isGuard(ship)?23:35))
                    .add(direction.multiply(PirateFleet.isGuard(ship)?-4:30));
                PirateNavigation.sail(world,ship,place,
                    ship.squaredDistanceTo(leader)>50*50?4:2,m.nav);
                return;
            }
            m.recalling=false;
            m.mode="patrol";
            patrol(world,ship,m);
            return;
        }
        ship.addCommandTag("ia_engaged");
        if(now%20==0)PirateSignals.battlePresence(world,ship);
        if(!target.vessel().getUuid().equals(m.opponent)){
            PirateSignals.emit(world,ship,PirateSignals.Signal.SIGHTED);
            m.opponent=target.vessel().getUuid();
            m.lastShot=now;m.observedShots=PirateGunnery.shots(ship);
            m.prevTarget=null;m.sampleAt=0;
            m.side=side(ship,target.vessel(),m);
        }
        if(m.prevTarget==null){m.prevTarget=target.vessel().getPos();
            m.sampleAt=now;
        }else if(now-m.sampleAt>=10){
            double span=Math.max(1,now-m.sampleAt);
            m.targetVelocity=target.vessel().getPos().subtract(m.prevTarget).multiply(1/span);
            m.prevTarget=target.vessel().getPos();
            m.sampleAt=now;
        }

        int shots=PirateGunnery.shots(ship);
        if(shots>m.observedShots){
            m.lastShot=now;m.observedShots=shots;
            if(PirateShipRoster.hullOf(ship)==PirateShipRoster.Hull.GALLEON ||
               PirateShipRoster.hullOf(ship)==PirateShipRoster.Hull.BRIGG)
                PirateSignals.emit(world,ship,PirateSignals.Signal.BROADSIDE);
        }
        // Give firing priority. Secondary tactics never suppress an
        // otherwise valid native cannon shot.
        PirateGunnery.tick(world,ship,target);
        PirateShipRoster.Hull hull=PirateShipRoster.hullOf(ship);
        Vec3d destination=target.vessel().getPos();
        int sails=3;
        Ship leader=PirateFleet.flagship(world,ship);
        boolean fleet=PirateFleet.isFleetShip(ship);
        if(fleet && hull==PirateShipRoster.Hull.DRAKKAR &&
           PirateSettings.ACTIVE.drakkarReinforcementsEnabled){
            if(now>=m.reinforcementAt){
                Ship needed=PirateCrew.reinforcementTarget(world,ship);
                UUID previous=m.reinforcement;
                m.reinforcement=needed==null?null:needed.getUuid();
                m.reinforcementAt=now+40;
                if(needed!=null && !needed.getUuid().equals(previous))
                    PirateSignals.emit(world,ship,PirateSignals.Signal.RALLY);
            }
            Ship needed=m.reinforcement==null?null:find(world,m.reinforcement);
            if(needed!=null && needed.isAlive() && !needed.isSinking() &&
               !needed.isSunken() && PirateCrew.alive(ship,PirateCrew.RESERVE)>=4){
                if(ship.squaredDistanceTo(needed)>13*13){
                    m.mode="fleet troop reinforcement";
                    PirateNavigation.sail(world,ship,needed.getPos(),2,m.nav);
                }else{
                    m.mode="transferring illager troops";
                    PirateNavigation.stop(ship);
                    if(now>=m.nextTransferAt){
                        if(PirateCrew.reinforce(ship,needed))
                            m.nextTransferAt=now+80;
                        else m.reinforcement=null;
                    }
                }
                return;
            }
        }
        if(PirateFleet.isFlagship(ship)) {
            m.mode="flagship broadside";
            destination=parallelPosition(ship,target.vessel(),m,54,14);
            sails=ship.squaredDistanceTo(destination)<26*26?1:2;
            m.recalling=(ship.getDamageWobbleStrength()>
                ship.getAttributes().maxHealth*.55f);
        } else if(fleet && leader!=null && !leader.isSunken() &&
                  !leader.isSinking()) {
            double leaderDist=Math.sqrt(ship.squaredDistanceTo(leader));
            boolean guard=PirateFleet.isGuard(ship);
            double limit=guard?35:115;
            double release=guard?24:48;
            if(leaderDist>limit)m.recalling=true;
            if(m.recalling && leaderDist<release)m.recalling=false;
            if(guard && leader.squaredDistanceTo(target.vessel())>55*55)
                m.recalling=true;
            // Once the player pushes close to a seriously damaged flagship,
            // attacking escorts abandon their chase to protect the mothership.
            if(!guard && leader.getDamageWobbleStrength()>
                leader.getAttributes().maxHealth*.3 &&
                leader.squaredDistanceTo(target.vessel())<60*60)
                m.recalling=true;
            if(m.recalling){
                m.mode="regroup";
                Vec3d direction=fromYaw(leader.getYaw());
                Vec3d right=new Vec3d(direction.z,0,-direction.x);
                destination=leader.getPos().add(right.multiply(guard?22:35))
                    .add(direction.multiply(guard?-4:35));
                sails=leaderDist>60?4:2;
            } else {
                m.mode=guard?"flagship guard":"fleet attacker";
                destination=combatDestination(world,ship,target,hull,m,now);
                sails=combatSails(ship,target,hull,m);
            }
        } else {
            m.recalling=false;
            destination=combatDestination(world,ship,target,hull,m,now);
            sails=combatSails(ship,target,hull,m);
        }
        if(now<m.evadeUntil && !PirateFleet.isFlagship(ship) &&
           !m.mode.equals("regroup")){
            m.mode="reposition after hit";
            Vec3d away=ship.getPos().subtract(target.vessel().getPos());
            if(away.horizontalLengthSquared()<.1)away=fromYaw(ship.getYaw());
            Vec3d right=new Vec3d(-away.z,0,away.x);
            destination=ship.getPos().add(away.normalize().multiply(35))
                .add(right.normalize().multiply(30*m.side));
            sails=4;
        }
        // Gunners report bad broadside angles; the helm adjusts only after
        // a sustained period without firing, preserving stable broadsides.
        int request=PirateGunnery.sideRequest(ship,now);
        if(request!=0 &&
           now-m.lastShot>110 &&
           !m.recalling &&
           hull!=PirateShipRoster.Hull.DRAKKAR && target.distanceSq()<75*75 &&
           now>=m.evadeUntil) {
            Vec3d diff=target.vessel().getPos().subtract(ship.getPos());
            double bearing=Math.toDegrees(Math.atan2(-diff.x,diff.z));
            double heading=bearing-(request>0?90:-90);
            destination=ship.getPos().add(fromYaw((float)heading).multiply(38));
            m.mode=request>0?"align starboard guns":"align port guns";
            sails=2;
        }
        // Optional operator tactical overrides remain useful for server
        // troubleshooting; natural encounters always start in auto mode.
        String forced="auto";
        for(String tag:ship.getCommandTags())if(tag.startsWith("ia_mode_")){
            forced=tag.substring("ia_mode_".length());break;
        }
        if(!forced.equals("auto")){
            Entity enemy=target.vessel();
            switch(forced) {
                case "patrol" -> {
                    m.mode="forced patrol";patrol(world,ship,m);return;
                }
                case "chase" -> {
                    m.mode="forced chase";destination=enemy.getPos();sails=4;
                }
                case "parallel", "broadside" -> {
                    m.mode="forced parallel";
                    destination=parallelPosition(ship,enemy,m,25,55);sails=2;
                }
                case "strafe" -> {
                    m.mode="forced strafe";
                    destination=parallelPosition(ship,enemy,m,22,35);sails=3;
                }
                case "ram" -> {
                    m.mode="forced ram";
                    Entity helm=enemy instanceof Ship targetShip &&
                        targetShip.getHelmsman()!=null?
                        targetShip.getHelmsman():enemy;
                    destination=helm.getPos().add(m.targetVelocity.multiply(12));
                    sails=4;
                }
                case "flee" -> {
                    m.mode="forced flee";
                    Vec3d away=ship.getPos().subtract(enemy.getPos());
                    if(away.horizontalLengthSquared()<1)away=fromYaw(ship.getYaw());
                    destination=ship.getPos().add(away.normalize().multiply(120));
                    sails=4;
                }
                case "evade" -> {
                    m.mode="forced evade";
                    Vec3d away=ship.getPos().subtract(enemy.getPos());
                    if(away.horizontalLengthSquared()<1)away=fromYaw(ship.getYaw());
                    destination=ship.getPos().add(away.normalize().multiply(65));
                    sails=4;
                }
            }
        }
        // Track a circling vessel with the native rudder rather than
        // repeatedly furling the sails and pursuing its old position.
        // Small Ships turns even at zero sail state; no translational
        // chase is necessary while a battery has a useful firing range.
        if(forced.equals("auto") && !m.recalling && now>=m.evadeUntil &&
           !m.mode.equals("ram") && hull!=PirateShipRoster.Hull.DRAKKAR &&
           PirateSettings.ACTIVE.disciplinedGunneryEnabled &&
           PirateNavigation.holdBroadside(world,ship,target.vessel(),
               m.targetVelocity,m.nav)){
            m.mode=Math.abs(m.nav.broadsideError)<=6 ?
                "holding broadside":"tracking enemy turn";
            if(now%20==0)defend(world,ship,target);
            return;
        }
        PirateNavigation.sail(world,ship,destination,sails,m.nav);
        if(now%20==0)defend(world,ship,target);
    }

    private static int personality(Ship ship){
        for(String tag:ship.getCommandTags())if(tag.startsWith("ia_personality_")){
            try{return Math.floorMod(Integer.parseInt(
                tag.substring("ia_personality_".length())),4);}
            catch(NumberFormatException ignored){}
        }
        return Math.floorMod(ship.getUuid().hashCode(),4);
    }
    private static String personalityName(Ship ship){
        return switch(personality(ship)){
            case 0 -> "Aggressive";
            case 1 -> "Patient";
            case 2 -> "Cautious";
            default -> "Opportunist";
        };
    }

    private static Vec3d combatDestination(ServerWorld world,Ship ship,
                                           PirateTactics.Target target,
                                           PirateShipRoster.Hull hull,Memory m,long now){
        double distance=Math.sqrt(target.distanceSq());
        int trait=personality(ship);
        if(distance>65){
            m.mode="chase";
            return target.vessel().getPos().add(m.targetVelocity.multiply(15));
        }
        if(hull==PirateShipRoster.Hull.DRAKKAR) {
            m.mode="boarding pursuit";
            return target.vessel().getPos().add(m.targetVelocity.multiply(8));
        }
        // One ram attempt per raider per lifetime, and only one against any
        // given enemy ship at once. The spent marker survives save/reload.
        if(m.ramUntil>0){
            boolean contact=ship.getBoundingBox().expand(1.2)
                .intersects(target.vessel().getBoundingBox());
            if(contact || now>=m.ramUntil ||
               m.ramOpponent==null ||
               !m.ramOpponent.equals(target.vessel().getUuid())){
                m.ramUntil=0;
                m.ramOpponent=null;
            }else{
                m.mode="ram";
                Entity helm=target.vessel();
                if(helm instanceof Ship enemy && enemy.getHelmsman()!=null)
                    helm=enemy.getHelmsman();
                return helm.getPos().add(m.targetVelocity.multiply(10));
            }
        }
        if(hull==PirateShipRoster.Hull.GALLEY &&
           !ship.getCommandTags().contains(RAM_USED) && distance<38 &&
           (now-m.lastShot>(trait==0?75:140) ||
            ship.getDamageWobbleStrength()<ship.getAttributes().maxHealth*.2) &&
           PirateTactics.confidence(ship)>(trait==0?45:65) &&
           !ramReserved(world,ship,target.vessel())){
            ship.addCommandTag(RAM_USED);
            ship.addCommandTag(RAM_TARGET+target.vessel().getUuidAsString());
            m.ramOpponent=target.vessel().getUuid();
            m.ramUntil=now+100;
            PirateSignals.emit(world,ship,PirateSignals.Signal.RAM);
            m.mode="ram";
            return target.vessel().getPos().add(m.targetVelocity.multiply(10));
        }
        // Persistent captain temperament modifies the preferred firing
        // corridor without replacing the vessel's actual class specialty.
        double temperamentSpacing=switch(trait){
            case 0 -> -5;  // aggressive, closer broadside
            case 1 -> 4;   // patient, deliberate gunnery at range
            case 2 -> 8;   // cautious, protect the hull
            default -> -2; // opportunist, close for crew harassment
        };
        switch(hull){
            case DHOW -> {
                if(!"strafe".equals(m.mode))PirateSignals.emit(world,ship,PirateSignals.Signal.STRAFE);
                m.mode="strafe";
                return parallelPosition(ship,target.vessel(),m,
                    24+temperamentSpacing,14);
            }
            case COG -> {
                m.mode="defensive broadside";
                return parallelPosition(ship,target.vessel(),m,
                    31+temperamentSpacing,16);
            }
            case BRIGG -> {
                m.mode="heavy broadside";
                return parallelPosition(ship,target.vessel(),m,
                    35+temperamentSpacing,18);
            }
            default -> {
                m.mode="parallel broadside";
                return parallelPosition(ship,target.vessel(),m,
                    25+temperamentSpacing,14);
            }
        }
    }

    private static boolean ramReserved(ServerWorld world,Ship self,Entity enemy){
        String opponent=RAM_TARGET+enemy.getUuidAsString();
        UUID fleet=PirateFleet.group(self);
        for(Ship other:loaded(world)){
            if(other==self || !other.isAlive() || other.isSinking())continue;
            // A fleet is allowed exactly one automatic ramming order in total.
            if(fleet!=null && fleet.equals(PirateFleet.group(other)) &&
               other.getCommandTags().contains(RAM_USED))return true;
            // Independent raiders may not ram the same victim concurrently.
            if(other.getCommandTags().contains(opponent))return true;
        }
        return false;
    }

    private static int combatSails(Ship ship,PirateTactics.Target target,
                                   PirateShipRoster.Hull hull,Memory m) {
        if(m.mode.equals("chase")||m.mode.equals("ram"))return 4;
        double distance=Math.sqrt(target.distanceSq());
        if(distance>48)return 4;
        if(distance<13)return 1;
        if(hull==PirateShipRoster.Hull.GALLEON)return 1;
        if(m.targetVelocity.horizontalLength()>.16 && distance>22)return 3;
        return distance<28?2:3;
    }

    private static Vec3d parallelPosition(Ship ship,Entity enemy,Memory m,
                                           double spacing,double lead){
        Vec3d forward=m.targetVelocity.horizontalLengthSquared()>.0005?
            m.targetVelocity.normalize():fromYaw(enemy.getYaw());
        Vec3d side=new Vec3d(forward.z,0,-forward.x);
        Vec3d point=enemy.getPos().add(side.multiply(m.side*spacing))
            .add(forward.multiply(lead));
        // Stable side, stable objective. Do not circle waiting for perfect aim.
        return point;
    }

    private static int side(Ship pirate,Entity enemy,Memory m) {
        Vec3d forward=fromYaw(enemy.getYaw());
        Vec3d right=new Vec3d(forward.z,0,-forward.x);
        return pirate.getPos().subtract(enemy.getPos()).dotProduct(right)>=0?1:-1;
    }
    private static Vec3d fromYaw(float yaw){
        double a=Math.toRadians(yaw);
        return new Vec3d(-Math.sin(a),0,Math.cos(a));
    }

    private static void patrol(ServerWorld world,Ship ship,Memory m){
        long now=world.getTime();
        if(m.patrolTarget==null || now>m.patrolUntil ||
           ship.squaredDistanceTo(m.patrolTarget)<12*12){
            Vec3d forward=fromYaw(ship.getYaw());
            for(int i=0;i<12;i++){
                double radians=(ship.getUuid().hashCode()+now/200+i*79)*
                    .024543692606;
                Vec3d point=ship.getPos().add(new Vec3d(
                    Math.sin(radians)*52,0,Math.cos(radians)*52));
                BlockPos at=BlockPos.ofFloored(point.x,world.getSeaLevel()-1,point.z);
                if(world.isChunkLoaded(at) &&
                   world.getFluidState(at).isOf(Fluids.WATER)){
                    m.patrolTarget=point;break;
                }
            }
            if(m.patrolTarget==null)m.patrolTarget=ship.getPos()
                .add(forward.multiply(40));
            m.patrolUntil=now+240;
        }
        PirateNavigation.sail(world,ship,m.patrolTarget,2,m.nav);
    }

    /** At most once/sec and only for spare crew in ranged combat. */
    private static void defend(ServerWorld world,Ship ship,PirateTactics.Target target){
        List<PillagerEntity> defenders=PirateCrew.occupants(ship,PirateCrew.RESERVE);
        if(defenders.isEmpty())return;
        LivingEntity victim=null;
        double best=30*30;
        for(ServerPlayerEntity player:world.getPlayers()){
            if(!player.isAlive() || player.isSpectator() || player.isCreative())continue;
            if(PirateTactics.vesselOf(world,player)!=target.vessel() &&
               !(player.isTouchingWater() && player.squaredDistanceTo(ship)<25*25))
                continue;
            double d=player.squaredDistanceTo(ship);
            if(d<best){victim=player;best=d;}
        }
        if(victim==null)return;
        for(PillagerEntity marine:defenders){
            if(!marine.canSee(victim))continue;
            if((world.getTime()/20+marine.getUuid().hashCode())%4!=0)continue;
            ArrowEntity arrow=new ArrowEntity(world,marine);
            arrow.pickupType=PersistentProjectileEntity.PickupPermission.DISALLOWED;
            arrow.setPosition(marine.getX(),marine.getEyeY()-.15,marine.getZ());
            Vec3d aim=victim.getEyePos().add(victim.getVelocity().multiply(6));
            Vec3d to=aim.subtract(arrow.getPos());
            arrow.setVelocity(to.x,to.y+to.horizontalLength()*.06,to.z,2.4f,5);
            world.spawnEntity(arrow);
            marine.swingHand(Hand.MAIN_HAND);
            world.playSound(null,marine.getBlockPos(),SoundEvents.ITEM_CROSSBOW_SHOOT,
                marine.getSoundCategory(),.7f,1);
        }
    }

    private static void prepareWreck(ContainerShip hold){
        Ship ship=(Ship)hold;
        if(!ship.getCommandTags().add("ia_wreck_ready"))return;
        if(ship.getCommandTags().contains("ia_no_loot")){
            for(int i=0;i<hold.size();i++)hold.setStack(i,ItemStack.EMPTY);
            hold.markDirty();
        }else{
            PirateCargo.salvage(ship);
        }
    }

    public static void startSinking(ServerWorld world,Ship ship,boolean administrative){
        if(ship.isSinking()||ship.isSunken())return;
        if(administrative)ship.addCommandTag("ia_no_loot");
        PirateNavigation.stop(ship);
        ship.startSinking();
        prepareWreck((ContainerShip)ship);
        PirateCrew.abandon(world,ship,administrative);
    }

    public static void clear(ServerWorld world,Ship ship,boolean administrative){
        PirateSurvivors.clearCrewFor(world,ship);
        for(Entity passenger:List.copyOf(ship.getPassengerList()))
            if(passenger.getCommandTags().contains(IllagerArmada.ILLAGER))
                passenger.discard();
        ship.discard();
        PirateBossBars.remove(ship);
        PirateGunnery.forget(ship);
        PirateCombat.forget(ship);
        PirateSignals.forget(ship);
        MEMORY.remove(ship.getUuid());
        Map<UUID,Ship> active=LOADED.get(world);
        if(active!=null)active.remove(ship.getUuid());
    }

    public static String status(Ship ship) {
        Memory m=MEMORY.get(ship.getUuid());
        return "Illager "+PirateShipRoster.hullOf(ship).display+
            " personality="+personalityName(ship)+
            " AI="+(m==null?"waiting":m.mode)+
            " crew="+ship.getPassengerList().size()+
            " helmsman="+(ship.getHelmsman()==PirateCrew.occupant(ship,PirateCrew.CAPTAIN))+
            " sails="+((com.talhanation.smallships.world.entity.ship.abilities.Sailable)ship).getSailState()+
            " requestedSails="+(m==null?0:m.nav.commandedSails)+
            " gunners="+PirateCrew.alive(ship,PirateCrew.GUNNER)+
            " reserves="+PirateCrew.alive(ship,PirateCrew.RESERVE)+
            " engineer="+PirateCrew.alive(ship,PirateCrew.ENGINEER)+
           " "+PirateCombat.status(ship)+
            " "+PirateGunnery.status(ship)+
            " ammo="+ammoStatus(ship)+
            " hull="+Math.round(ship.getAttributes().maxHealth-
                ship.getDamageWobbleStrength())+"/"+Math.round(ship.getAttributes().maxHealth);
    }
    private static String ammoStatus(Ship ship){
        if(!(ship instanceof ContainerShip hold))return "none";
        return hold.getAmmoInHold(CannonBallItem.Type.BALL)+"/"+
            hold.getAmmoInHold(CannonBallItem.Type.CHAINED)+"/"+
            hold.getAmmoInHold(CannonBallItem.Type.GRAPE);
    }

}
