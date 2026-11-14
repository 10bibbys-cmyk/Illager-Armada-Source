package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Local 4-ship fleet separation. This only looks at already tracked, loaded
 * Armada ships. Zero world/entity searches, chunk loads or pathfinding jobs.
 * The flagship holds its line; smaller ships yield to nearby fleetmates.
 */
public final class PirateFleetAvoidance {
    private PirateFleetAvoidance(){}

    public static Vec3d waypoint(ServerWorld world,Ship ship,Vec3d destination,
                                 PirateNavigation.Memory memory){
        if(!PirateSettings.ACTIVE.fleetCollisionAvoidanceEnabled ||
           !PirateFleet.isFleetShip(ship) || PirateFleet.isFlagship(ship))
            return destination;
        long now=world.getTime();
        if(now<memory.fleetAvoidanceUntil && memory.fleetAvoidanceTarget!=null)
            return memory.fleetAvoidanceTarget;
        memory.fleetAvoidanceUntil=now+15;
        memory.fleetAvoidanceTarget=null;
        UUID group=PirateFleet.group(ship);
        if(group==null)return destination;

        Vec3d self=ship.getPos();
        Vec3d desired=destination.subtract(self);
        desired=new Vec3d(desired.x,0,desired.z);
        if(desired.horizontalLengthSquared()<1)return destination;
        Vec3d course=desired.normalize();
        Vec3d velocity=ship.getVelocity();
        Vec3d predictSelf=self.add(velocity.multiply(28));
        Vec3d push=Vec3d.ZERO;
        for(Ship ally:PirateShips.loaded(world)){
            if(ally==ship || !ally.isAlive() || ally.isSinking() ||
               ally.isSunken() || !group.equals(PirateFleet.group(ally)))
                continue;
            Vec3d other=ally.getPos();
            double safe=radius(ship)+radius(ally)+3.5;
            if(self.squaredDistanceTo(other)>65*65)continue;
            Vec3d future=other.add(ally.getVelocity().multiply(28));
            Vec3d away=new Vec3d(predictSelf.x-future.x,0,predictSelf.z-future.z);
            double separation=away.horizontalLength();
            Vec3d currentAway=new Vec3d(self.x-other.x,0,self.z-other.z);
            double current=currentAway.horizontalLength();
            // Only yield if nearby or predicted to converge within ~1.4 s.
            if(current>safe*1.55 && separation>safe*1.35)continue;
            if(separation<.01)away=currentAway;
            if(away.horizontalLengthSquared()<.0001)
                away=new Vec3d(-course.z,0,course.x);
            double severity=Math.max(0,1-Math.min(current,separation)/(safe*1.55));
            double priority=PirateFleet.isFlagship(ally)?2:1;
            push=push.add(away.normalize().multiply((14+22*severity)*priority));
        }
        if(push.horizontalLengthSquared()<.1)return destination;
        // Favour crossing behind, not through, the other ship. Hold each
        // avoidance objective briefly to prevent alternating left/right turns.
        Vec3d corrected=ship.getPos().add(course.multiply(13)).add(push);
        memory.fleetAvoidanceTarget=corrected;
        return corrected;
    }

    private static double radius(Ship ship){
        return switch(PirateShipRoster.hullOf(ship)){
            case GALLEON -> 12.5;
            case BRIGG -> 9;
            case COG -> 8;
            case CARAVEL -> 7;
            case DHOW -> 7;
            case DRAKKAR -> 6.5;
            case GALLEY -> 6;
        };
    }
}
