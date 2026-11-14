package dev.illagerarmada;

import com.talhanation.smallships.world.entity.cannon.ShipCannon;
import com.talhanation.smallships.world.entity.cannon.CannonGeometry;
import com.talhanation.smallships.world.entity.ship.ContainerShip;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.hitbox.ShipPartEntity;
import net.minecraft.util.math.Box;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.item.CannonBallItem;
import com.talhanation.smallships.world.item.CannonAmmoSelection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Native Small Ships cannon fuse, recoil, real ballistics and grouped Galleon salvos. */
public final class PirateGunnery {
    private static final double SPEED=2.6,DRAG=.99,GRAVITY=.06,FUSE=14.5;
    private static final int REARM_TICKS=12;
    private static final Map<UUID,Memory> CACHE=new HashMap<>();
    private PirateGunnery(){}

    private static final class Memory {
        final Map<Integer,Long> nextAttempt=new HashMap<>();
        final Map<Boolean,Long> sideNext=new HashMap<>();
        Vec3d targetPos,piratePos,targetVelocity=Vec3d.ZERO,pirateVelocity=Vec3d.ZERO;
        UUID target;
        long sampledAt;
        int ordered;
        int requestedSide;
        long requestUntil;
        final Map<Integer,Tracking> primed=new HashMap<>();
    }
    private record Tracking(UUID opponent,CannonBallItem.Type ammo,long started,
                            double yawError,double pitchError){}
    private record Aim(float elevation,float rotation,double range) {}
    private static final double MIN_RANGE=11,MAX_OFFSET=8.0;

    // Native Small Ships has individual cannon aim NBT when a gunner sits
    // in a mapped station; unmanned grouped Galleon slots use side aim.
    private static void setAim(Cannonable cannons,int slot,boolean right,Aim aim){
        if(cannons.hasPerCannonAim(slot))
            cannons.setCannonAim(slot,right,aim.elevation(),aim.rotation());
        else cannons.setCannonAim(right,aim.elevation(),aim.rotation());
    }
    public static int shots(Ship ship){
        Memory m=CACHE.get(ship.getUuid());
        return m==null?0:m.ordered;
    }
    public static void forget(Ship ship){CACHE.remove(ship.getUuid());}
    public static void reset(){CACHE.clear();}
    public static int sideRequest(Ship ship,long now){
        Memory m=CACHE.get(ship.getUuid());
        return m!=null && now<m.requestUntil?m.requestedSide:0;
    }
    private static final String ACCURACY_TAG="ia_accuracy_";
    public static int accuracy(PillagerEntity gunner){
        for(String tag:gunner.getCommandTags()){
            if(!tag.startsWith(ACCURACY_TAG))continue;
            // The prefix is 12 characters, not 15. Use its actual length so
            // a valid tag (e.g. ia_accuracy_80) never crashes the server.
            try{return MathHelper.clamp(
                Integer.parseInt(tag.substring(ACCURACY_TAG.length())),50,95);}
            catch(NumberFormatException ignored){}
        }
        return 50+Math.floorMod(gunner.getUuid().hashCode(),46);
    }

    /** Low-cost combat readiness check for rudder-only broadside tracking. */
    public static boolean canEngage(Ship ship){
        if(!(ship instanceof Cannonable battery) ||
           !(ship instanceof ContainerShip hold) ||
           ship.isSinking() ||ship.isSunken())return false;
        if(hold.getAmmoInHold(CannonBallItem.Type.BALL)<=0 &&
           hold.getAmmoInHold(CannonBallItem.Type.CHAINED)<=0 &&
           hold.getAmmoInHold(CannonBallItem.Type.GRAPE)<=0)return false;
        for(ShipCannon cannon:battery.getCannons()){
            if(PirateCrew.gunnerFor(ship,cannon.getSlotIndex())!=null)return true;
        }
        return false;
    }

    public static boolean hasFiringLane(Ship ship,Entity enemy){
        if(!(ship instanceof Cannonable battery) ||
           !(ship instanceof ContainerShip hold) ||
           !hasAmmunition(hold) ||enemy==null ||!enemy.isAlive())return false;
        double distance=ship.distanceTo(enemy);
        if(distance<MIN_RANGE+2 ||
           distance>PirateSettings.ACTIVE.cannonMaxFireRange)return false;
        Vec3d diff=enemy.getPos().subtract(ship.getPos());
        double bearing=Math.toDegrees(Math.atan2(-diff.x,diff.z));
        for(ShipCannon gun:battery.getCannons()){
            if(PirateCrew.gunnerFor(ship,gun.getSlotIndex())==null)continue;
            double sideYaw=ship.getYaw()+(gun.isRightSided()?90:-90);
            if(Math.abs(MathHelper.wrapDegrees(bearing-sideYaw))<7.2)return true;
        }
        return false;
    }

    public static void tick(ServerWorld world,Ship ship,PirateTactics.Target target){
        if(!(ship instanceof Cannonable cannons) ||
           !(ship instanceof ContainerShip hold) ||target==null ||
           ship.isSinking()||ship.isSunken())return;
        Entity enemy=target.vessel();
        if(!enemy.isAlive() || !hasAmmunition(hold))return;
        long now=world.getTime();
        Memory m=CACHE.computeIfAbsent(ship.getUuid(),id->new Memory());
        if(!enemy.getUuid().equals(m.target)){
            m.target=enemy.getUuid();
            m.targetPos=m.piratePos=null;
            m.targetVelocity=m.pirateVelocity=Vec3d.ZERO;
            m.sampledAt=0;
            m.primed.clear();
        }
        // Current velocity matters most. Averaging only ten-tick positions can
        // overshoot a player who has just dropped sail and come to a stop.
        if(m.targetPos==null){
            m.targetPos=enemy.getPos();
            m.piratePos=ship.getPos();
            m.sampledAt=now;
        }else if(now-m.sampledAt>=5){
            double elapsed=Math.max(1,now-m.sampledAt);
            Vec3d measured=enemy.getPos().subtract(m.targetPos).multiply(1/elapsed);
            Vec3d measuredSelf=ship.getPos().subtract(m.piratePos).multiply(1/elapsed);
            Vec3d current=enemy.getVelocity();
            m.targetVelocity=current.horizontalLengthSquared()<.0004?
                Vec3d.ZERO:current.multiply(.8).add(measured.multiply(.2));
            m.pirateVelocity=ship.getVelocity().multiply(.8).add(measuredSelf.multiply(.2));
            // Ignore chunk load jumps and teleports; those aren't sea velocity.
            if(m.targetVelocity.horizontalLengthSquared()>1)m.targetVelocity=Vec3d.ZERO;
            if(m.pirateVelocity.horizontalLengthSquared()>1)m.pirateVelocity=Vec3d.ZERO;
            m.targetPos=enemy.getPos();
            m.piratePos=ship.getPos();
            m.sampledAt=now;
        }

        // Re-aim each lit fuse as the target manoeuvres. Small Ships samples
        // the cannon's aim when its 10–19 tick fuse actually expires; an aim
        // chosen only on ignition is otherwise stale when the shot launches.
        boolean busyPort=false,busyStarboard=false;
        for(ShipCannon barrel:cannons.getCannons()){
            int slot=barrel.getSlotIndex();
            if(!barrel.isFuzing()){
                m.primed.remove(slot);
                continue;
            }
            if(barrel.isRightSided())busyStarboard=true;
            else busyPort=true;
            Tracking armed=m.primed.get(slot);
            if(armed==null || !armed.opponent().equals(enemy.getUuid()))continue;
            PillagerEntity gunner=PirateCrew.gunnerFor(ship,slot);
            double speed=SPEED*armed.ammo().speedMultiplier;
            if(gunner!=null)
                speed=SPEED*cannons.getShotSpeedMultiplier(gunner,true);
            double remaining=Math.max(0,FUSE-(now-armed.started()));
            Aim adjusted=calculate(ship,barrel,enemy,target,armed.ammo(),
                m.targetVelocity,m.pirateVelocity,speed,remaining,
                armed.yawError(),armed.pitchError());
            if(adjusted!=null)
                setAim(cannons,slot,barrel.isRightSided(),adjusted);
        }

        // Ready cannons may fire only when the predicted impact lies inside
        // their real ±10° horizontal traverse and the configured range.
        if(enemy.squaredDistanceTo(ship)>
           Math.pow(PirateSettings.ACTIVE.cannonMaxFireRange,2))return;

        // One fresh fuse per side at a time; preserve sequential broadsides.
        for(ShipCannon barrel:cannons.getCannons()){
            boolean right=barrel.isRightSided();
            if(right?busyStarboard:busyPort)continue;
            int slot=barrel.getSlotIndex();
            if(now<m.nextAttempt.getOrDefault(slot,0L) ||
               now<m.sideNext.getOrDefault(right,0L) ||
               barrel.isCooldown()||barrel.isFuzing())continue;
            PillagerEntity gunner=PirateCrew.gunnerFor(ship,slot);
            if(gunner==null)continue;
            m.nextAttempt.put(slot,now+10);
            double range=Math.sqrt(target.distanceSq());
            CannonBallItem.Type choice=selectAmmo(hold,target,m.targetVelocity,enemy,range);
            if(choice==null)continue;
            CannonAmmoSelection.set(gunner,choice);
            CannonBallItem loaded=cannons.getCannonBallToShoot(gunner);
            if(loaded==null)continue;
            CannonBallItem.Type actual=loaded.getType();
            if(actual==CannonBallItem.Type.CHAINED &&
               range>PirateSettings.ACTIVE.chainShotMaxRange)continue;
            if(actual==CannonBallItem.Type.GRAPE && range>27)continue;
            double speed=SPEED*cannons.getShotSpeedMultiplier(gunner,true);
            int skill=accuracy(gunner);
            // Skill scales dispersion instead of an arbitrary 50% failure roll
            // that previously launched cannonballs several degrees off target.
            double dispersion=(100-skill)/100.0;
            Random random=new Random(gunner.getUuid().getLeastSignificantBits()^
                now^((long)slot<<32));
            double multiplier=PirateSettings.ACTIVE.gunnerAccuracyMultiplier;
            double yawError=random.nextGaussian()*(.10+dispersion*.60)*multiplier;
            double pitchError=random.nextGaussian()*(.08+dispersion*.48)*multiplier;
            Aim solution=calculate(ship,barrel,enemy,target,actual,
                m.targetVelocity,m.pirateVelocity,speed,FUSE,yawError,pitchError);
            if(solution==null){
                // Align deliberately rather than firing into the sea.
                if(now>=m.requestUntil){
                    m.requestedSide=right?1:-1;
                    m.requestUntil=now+70;
                }
                continue;
            }
            if(PirateSettings.ACTIVE.disciplinedGunneryEnabled &&
               ship.getVelocity().horizontalLengthSquared()>.10 &&
               range>27)continue;
            setAim(cannons,slot,right,solution);
            if(!hasAmmunition(hold))continue;
            barrel.trigger(gunner);
            m.primed.put(slot,new Tracking(enemy.getUuid(),actual,now,
                yawError,pitchError));
            m.ordered++;
            m.sideNext.put(right,now+REARM_TICKS);
            if(m.requestedSide==(right?1:-1)){
                m.requestUntil=0;
            }
            if(right)busyStarboard=true;
            else busyPort=true;
        }
    }

    private static Aim calculate(Ship ship,ShipCannon barrel,Entity enemy,
                                 PirateTactics.Target target,
                                 CannonBallItem.Type ball,Vec3d velocity,
                                 Vec3d shooterVelocity,double shotSpeed,
                                 double remainingFuse,double errorYaw,
                                 double errorPitch){
        if(shotSpeed<.3)return null;
        Vec3d targetPoint=aimPoint(enemy,target,ball,barrel.getGlobalPosition());
        Vec3d pivot=barrel.getGlobalPosition().add(
            0,CannonGeometry.shipTrunnionHeight(),0);
        Vec3d futurePivot=pivot.add(shooterVelocity.multiply(remainingFuse));
        // The shot starts roughly one block ahead of the trunnion, not at the
        // cannon's deck mount. Include this in travel time and trajectory.
        double spawnDistance=CannonGeometry.spawnDistance();
        double distance=horizontal(futurePivot,targetPoint);
        double flight=Math.max(1,(distance-spawnDistance)/shotSpeed);
        Vec3d intercept=targetPoint;
        double pitch=0;
        for(int i=0;i<4;i++){
            intercept=targetPoint.add(velocity.multiply(remainingFuse+flight));
            distance=horizontal(futurePivot,intercept)-spawnDistance;
            if(distance<MIN_RANGE-2 ||distance>
               PirateSettings.ACTIVE.cannonMaxFireRange+8)return null;
            double deltaY=intercept.y-futurePivot.y;
            double solved=flightTime(Math.max(1,distance),deltaY,shotSpeed);
            if(solved<0)return null;
            flight=solved;
            double drag=dragSum(flight);
            double vy=(deltaY+GRAVITY/(1-DRAG)*(flight-drag))/drag;
            pitch=Math.toDegrees(Math.atan2(vy,Math.max(1,distance)/drag));
        }
        if(!Double.isFinite(pitch))return null;
        double yaw=Math.toDegrees(Math.atan2(
            -(intercept.x-futurePivot.x),intercept.z-futurePivot.z));
        boolean right=barrel.isRightSided();
        double sideYaw=ship.getYaw()+(right?90:-90);
        double offset=MathHelper.wrapDegrees(yaw-sideYaw);
        if(Math.abs(offset)>MAX_OFFSET ||
           pitch< -8 ||pitch>28)return null;
        float rotate=(float)((offset+errorYaw)*(right?1:-1));
        float elevate=(float)(pitch+errorPitch);
        if(Math.abs(rotate)>9.4 || elevate< -9 || elevate>29)return null;
        return new Aim(elevate,rotate,distance);
    }

    private static Vec3d aimPoint(Entity enemy,PirateTactics.Target target,
                                  CannonBallItem.Type ammo,Vec3d muzzle){
        if(ammo==CannonBallItem.Type.CHAINED && enemy instanceof Ship vessel){
            Vec3d best=null;
            double score=Double.MAX_VALUE;
            for(ShipPartEntity.Definition part:vessel.getParts()){
                if(!part.mast())continue;
                Box box=part.boxAt(vessel.getX(),vessel.getY(),vessel.getZ(),
                    vessel.getYaw());
                double preferred=vessel.getY()+4.0;
                // Aim at lower sail / mast, avoiding high, hard-to-hit rigging.
                double y=MathHelper.clamp(preferred,box.minY+.4,box.maxY-.4);
                Vec3d center=new Vec3d((box.minX+box.maxX)*.5,y,
                    (box.minZ+box.maxZ)*.5);
                double candidate=center.squaredDistanceTo(muzzle) +
                    Math.pow(Math.abs(y-preferred)*3.0,2);
                if(candidate<score){score=candidate;best=center;}
            }
            if(best!=null)return best;
        }
        if(ammo==CannonBallItem.Type.GRAPE && target.player()!=null &&
           target.player().isAlive())return target.player().getEyePos();
        // A hull hit is aimed low, around the waterline / main deck.
        return enemy.getPos().add(0,Math.min(1.1,enemy.getHeight()*.28),0);
    }

    private static boolean hasAmmunition(ContainerShip hold){
        for(int i=0;i<hold.size();i++)
            if(hold.getStack(i).getItem() instanceof CannonBallItem)return true;
        return false;
    }

    private static CannonBallItem.Type selectAmmo(ContainerShip hold,
            PirateTactics.Target target,Vec3d velocity,Entity enemy,double range){
        if(PirateSettings.ACTIVE.intelligentAmmoEnabled){
            if(target.crew()>=2 && range<23 &&
               hold.getAmmoInHold(CannonBallItem.Type.GRAPE)>0)
                return CannonBallItem.Type.GRAPE;
            if(enemy instanceof Ship vessel &&
               range>=13 &&range<=PirateSettings.ACTIVE.chainShotMaxRange &&
               velocity.horizontalLengthSquared()>.0009 &&
               vessel.getParts().stream().anyMatch(ShipPartEntity.Definition::mast) &&
               hold.getAmmoInHold(CannonBallItem.Type.CHAINED)>0)
                return CannonBallItem.Type.CHAINED;
        }
        if(hold.getAmmoInHold(CannonBallItem.Type.BALL)>0)
            return CannonBallItem.Type.BALL;
        if(range<23 && hold.getAmmoInHold(CannonBallItem.Type.GRAPE)>0)
            return CannonBallItem.Type.GRAPE;
        if(range<=PirateSettings.ACTIVE.chainShotMaxRange &&
           enemy instanceof Ship vessel &&
           vessel.getParts().stream().anyMatch(ShipPartEntity.Definition::mast) &&
           hold.getAmmoInHold(CannonBallItem.Type.CHAINED)>0)
            return CannonBallItem.Type.CHAINED;
        return null; // Hold fire until a useful shot is possible.
    }
    private static double horizontal(Vec3d a,Vec3d b){
        return Math.hypot(a.x-b.x,a.z-b.z);
    }
    private static double dragSum(double ticks){
        return (1-Math.pow(DRAG,ticks))/(1-DRAG);
    }
    private static double requiredSpeed(double distance,double deltaY,double t){
        double s=dragSum(t);
        double vx=distance/s,vy=(deltaY+GRAVITY/(1-DRAG)*(t-s))/s;
        return Math.hypot(vx,vy);
    }
    private static double flightTime(double distance,double dy,double speed){
        double prior=.5;
        for(double t=1;t<=65;t++){
            if(requiredSpeed(distance,dy,t)<=speed){
                double lo=prior,hi=t;
                for(int j=0;j<10;j++){
                    double mid=(lo+hi)*.5;
                    if(requiredSpeed(distance,dy,mid)<=speed)hi=mid;
                    else lo=mid;
                }
                return hi;
            }
            prior=t;
        }
        return -1;
    }
    public static String status(Ship ship){
        Memory m=CACHE.get(ship.getUuid());
        return "shots="+(m==null?0:m.ordered)+
            " turnRequest="+(m==null?0:m.requestedSide);
    }
}
