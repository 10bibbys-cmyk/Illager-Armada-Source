package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.entity.cannon.ShipCannon;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluids;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Bounded, loaded-chunk-only navigation for NPC-driven Small Ships.
 * Small Ships supports sail states 1..4 under input controls; sail state 0
 * cannot be raised by pressing forward, so we explicitly bootstrap to 1.
 */
public final class PirateNavigation {
    private PirateNavigation() {}

    public static final class Memory {
        public double chosenHeading = Double.NaN;
        public long recheckAt;
        public long progressCheckedAt;
        public Vec3d progressPosition;
        public int stuckChecks;
        public boolean obstructed;
        public int clearance;
        public int requestedSails;
        public int commandedSails;
        public float headingError;
        public long fleetAvoidanceUntil;
        public Vec3d fleetAvoidanceTarget;
        public Vec3d retreatWaypoint;
        public long retreatRecheckAt;
        public boolean retreatRouting;
        public boolean holdingBroadside;
        public int holdingSide;
        public long nextSideSwitch;
        public float broadsideError;
    }

    /**
     * Hold a broadside against a vessel moving around us. Small Ships' native
     * controlShip() rotates the rudder even with sail state zero; steering in
     * place avoids the perpetual pursuit loops that otherwise let a fast boat
     * sit behind an enemy's guns. Only nearby active battles call this (5t).
     *
     * Select a manned battery, anticipate roughly half a second of lateral
     * motion, and keep the same side until switching has clear benefit.
     * No block queries, chunks, additional entities, or world scans.
     */
    public static boolean holdBroadside(ServerWorld world,Ship ship,Entity target,
                                        Vec3d targetVelocity,Memory memory){
        if(!PirateSettings.ACTIVE.antiOrbitTrackingEnabled ||
           !(ship instanceof Cannonable cannons) ||target==null ||
           !PirateGunnery.canEngage(ship))return releaseBroadside(memory);
        Vec3d relative=target.getPos().subtract(ship.getPos());
        double distance=Math.hypot(relative.x,relative.z);
        if(distance<12.5 ||distance>PirateSettings.ACTIVE.antiOrbitTrackingRange)
            return releaseBroadside(memory);

        boolean port=false,starboard=false;
        for(ShipCannon cannon:cannons.getCannons()){
            if(PirateCrew.gunnerFor(ship,cannon.getSlotIndex())==null)continue;
            if(cannon.isRightSided())starboard=true;else port=true;
        }
        if(!port && !starboard)return releaseBroadside(memory);

        // Close orbiters change bearing rapidly. Lead their current lateral
        // velocity without forcing a rapid turning chase around their position.
        double lead=MathHelper.clamp(6+distance*.09,7,12);
        Vec3d predicted=relative.add(new Vec3d(
            targetVelocity.x*lead,0,targetVelocity.z*lead));
        if(predicted.horizontalLengthSquared()<1)predicted=relative;
        double bearing=Math.toDegrees(Math.atan2(-predicted.x,predicted.z));
        double starError=MathHelper.wrapDegrees((float)(
            bearing-90-ship.getYaw()));
        double portError=MathHelper.wrapDegrees((float)(
            bearing+90-ship.getYaw()));
        int side=memory.holdingSide;
        if(side==0 ||side>0 && !starboard ||side<0 && !port){
            side=starboard && (!port ||Math.abs(starError)<=Math.abs(portError))?1:-1;
        }else{
            double current=side>0?Math.abs(starError):Math.abs(portError);
            double opposite=side>0?Math.abs(portError):Math.abs(starError);
            boolean otherReady=side>0?port:starboard;
            // At bow/stern crossings hold the chosen side rather than flip
            // every 5 ticks. Switching is deliberate and limited to 1/second.
            if(otherReady && world.getTime()>=memory.nextSideSwitch &&
               opposite+18<current){
                side=-side;
                memory.nextSideSwitch=world.getTime()+20;
            }
        }
        memory.holdingSide=side;
        float error=(float)(side>0?starError:portError);
        memory.broadsideError=error;
        memory.holdingBroadside=true;
        // Native ship rotation works with furled sails. The hull remains in
        // the firing zone while the captain tracks a changing bearing.
        Sailable sails=(Sailable)ship;
        if(sails.getSailState()!=0)sails.setSailState((byte)0);
        ship.setForward(false);
        ship.setBackward(false);
        ship.setLeft(error < -4.0f);
        ship.setRight(error > 4.0f);
        return true;
    }

    /** Restore normal waypoint steering only when leaving broadside defence. */
    private static boolean releaseBroadside(Memory memory){
        if(memory.holdingBroadside){
            memory.holdingBroadside=false;
            memory.holdingSide=0;
            memory.chosenHeading=Double.NaN;
            memory.recheckAt=0;
            memory.progressPosition=null;
            memory.stuckChecks=0;
        }
        return false;
    }

    public static void stop(Ship ship) {
        ship.setForward(false);
        ship.setBackward(false);
        ship.setLeft(false);
        ship.setRight(false);
        ((Sailable) ship).setSailState((byte) 0);
    }

    public static void sail(ServerWorld world, Ship ship, Vec3d destination,
                            int requestedSails, Memory memory) {
        releaseBroadside(memory);
        if (destination == null) {
            stop(ship);
            return;
        }
        if(!memory.retreatRouting)
            destination=PirateFleetAvoidance.waypoint(world,ship,destination,memory);
        Vec3d delta = destination.subtract(ship.getPos());
        if (delta.horizontalLengthSquared() < 16.0) {
            stop(ship);
            return;
        }

        long now = world.getTime();
        double desiredHeading = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        if (now >= memory.recheckAt || Double.isNaN(memory.chosenHeading) ||
            Math.abs(MathHelper.wrapDegrees(desiredHeading - memory.chosenHeading)) > 60.0) {
            memory.chosenHeading = chooseHeading(world, ship, desiredHeading, memory);
            memory.clearance = safeSegments(world, ship, memory.chosenHeading);
            memory.obstructed = memory.clearance < 2;
            memory.recheckAt = now + 10;
        }

        // Check actual progress at two-second intervals. This is long enough to
        // let Small Ships accelerate and change sails before deciding we're stuck.
        if (memory.progressPosition == null || now - memory.progressCheckedAt >= 40) {
            if (memory.progressPosition != null) {
                double travelled = horizontalSq(ship.getPos(), memory.progressPosition);
                if (travelled < 1.5 && ((Sailable)ship).getSailState() >= 2 &&
                    requestedSails >= 2) {
                    memory.stuckChecks = Math.min(6, memory.stuckChecks + 1);
                } else {
                    memory.stuckChecks = Math.max(0, memory.stuckChecks - 1);
                }
            }
            memory.progressPosition = ship.getPos();
            memory.progressCheckedAt = now;
        }

        float error = MathHelper.wrapDegrees((float)(memory.chosenHeading - ship.getYaw()));
        memory.headingError = error;
        ship.setLeft(error < -7);
        ship.setRight(error > 7);

        // Full speed is permitted for safe, straight pursuit or escape. Reduce
        // power for turns and close manoeuvres; never permanently furl sails
        // during active navigation, which would strand the ship.
        int sails = MathHelper.clamp(requestedSails, 1, 4);
        if (Math.abs(error) > 105) sails = Math.min(sails, 1);
        else if (Math.abs(error) > 55) sails = Math.min(sails, 2);
        if (memory.clearance == 2) sails = Math.min(sails, 3);
        else if (memory.clearance <= 1) sails = Math.min(sails, 1);
        if (memory.stuckChecks >= 2) sails = Math.min(sails, 2);

        memory.requestedSails = requestedSails;
        memory.commandedSails = sails;
        setSailTarget(ship, sails);
    }

    /**
     * Retreats are not simply "sail away from the target": that vector may
     * point straight into a beach. Pick the most open loaded-water corridor,
     * biasing away from the threat. Only replan every 60 ticks or at a waypoint.
     * Zero chunk loads and no dynamic entity searches.
     */
    public static void retreat(ServerWorld world,Ship ship,Entity threat,
                               int requestedSails,Memory memory){
        if(!PirateSettings.ACTIVE.openWaterRetreatEnabled){
            Vec3d delta=threat==null?fromYaw(ship.getYaw()):
                ship.getPos().subtract(threat.getPos());
            delta=new Vec3d(delta.x,0,delta.z);
            if(delta.horizontalLengthSquared()<1)delta=fromYaw(ship.getYaw());
            sail(world,ship,ship.getPos().add(delta.normalize().multiply(95)),
                requestedSails,memory);
            return;
        }
        long now=world.getTime();
        if(memory.retreatWaypoint==null || now>=memory.retreatRecheckAt ||
           ship.squaredDistanceTo(memory.retreatWaypoint)<20*20 ||
           memory.obstructed && now+20>=memory.retreatRecheckAt){
            Vec3d away=threat==null?fromYaw(ship.getYaw()):
                ship.getPos().subtract(threat.getPos());
            away=new Vec3d(away.x,0,away.z);
            if(away.horizontalLengthSquared()<1)away=fromYaw(ship.getYaw());
            double ideal=Math.toDegrees(Math.atan2(-away.x,away.z));
            double bestScore=-1e9;
            double bestHeading=ideal;
            int bestClear=0;
            // 12 alternatives at 30 degree intervals. Max 6x3x10 water
            // reads each; throttle to roughly once every three seconds.
            for(int step=-6;step<=5;step++){
                double offset=step*30;
                double heading=ideal+offset;
                int clear=openWaterSteps(world,ship,heading);
                if(clear<2)continue;
                double score=clear*35.0-Math.abs(offset)*.13-
                    Math.abs(MathHelper.wrapDegrees(heading-ship.getYaw()))*.09;
                if(clear<4)score-=(4-clear)*75;
                if(score>bestScore){
                    bestScore=score;bestHeading=heading;bestClear=clear;
                }
            }
            memory.retreatRecheckAt=now+60;
            if(bestClear<2){
                memory.retreatWaypoint=null;
                stop(ship);return;
            }
            memory.retreatWaypoint=ship.getPos().add(
                fromYaw(bestHeading).multiply(Math.min(90,bestClear*14-8)));
        }
        Vec3d course=memory.retreatWaypoint.subtract(ship.getPos());
        if(course.horizontalLengthSquared()<9){
            stop(ship);return;
        }
        int ahead=openWaterSteps(world,ship,
            Math.toDegrees(Math.atan2(-course.x,course.z)));
        if(ahead<2){
            memory.retreatRecheckAt=0;
            stop(ship);return;
        }
        // Coastlines and shallow passages are traversed at modest speed.
        int safeSails=Math.min(requestedSails,ahead<4?1:ahead<6?2:3);
        memory.retreatRouting=true;
        try{sail(world,ship,memory.retreatWaypoint,safeSails,memory);}
        finally{memory.retreatRouting=false;}
    }

    private static Vec3d fromYaw(double yaw){
        double rad=Math.toRadians(yaw);
        return new Vec3d(-Math.sin(rad),0,Math.cos(rad));
    }

    private static int openWaterSteps(ServerWorld world,Ship ship,double heading){
        double radians=Math.toRadians(heading);
        double forwardX=-Math.sin(radians),forwardZ=Math.cos(radians);
        double rightX=forwardZ,rightZ=-forwardX;
        int seaY=world.getSeaLevel()-1;
        PirateShipRoster.Hull hull=PirateShipRoster.hullOf(ship);
        int draft=Math.max(3,hull.depth);
        double width=Math.max(3,hull.clearance-1);
        int clear=0;
        for(int i=1;i<=6;i++){
            double distance=i*14.0;
            boolean safe=true;
            for(double side:new double[]{-width,0,width}){
                BlockPos point=BlockPos.ofFloored(
                    ship.getX()+forwardX*distance+rightX*side,seaY,
                    ship.getZ()+forwardZ*distance+rightZ*side);
                if(!world.isChunkLoaded(point)){safe=false;break;}
                for(int depth=0;depth<draft;depth++){
                    if(!world.getFluidState(point.down(depth)).isOf(Fluids.WATER)){
                        safe=false;break;
                    }
                }
                if(!safe)break;
            }
            if(!safe)break;
            clear++;
        }
        return clear;
    }

    private static void setSailTarget(Ship ship, int target) {
        Sailable sails = (Sailable) ship;
        byte current = sails.getSailState();
        if (current == 0 && target > 0) {
            // Native Small Ships needs one raised sail state before forward
            // throttle can increase it further.
            sails.setSailState((byte) 1);
            current = 1;
        }
        ship.setForward(current < target);
        ship.setBackward(current > target);
    }

    private static double chooseHeading(ServerWorld world, Ship ship,
                                        double ideal, Memory memory) {
        // The overwhelming majority of ocean navigation has a clear direct
        // route. Skip 11 expensive alternative heading scans in that case.
        if(safeSegments(world,ship,ideal)==3)return ideal;
        double[] offsets = {0, 20, -20, 40, -40, 70, -70, 110, -110, 155, -155, 180};
        double choice = ideal;
        double bestScore = -1e9;
        for (double offset : offsets) {
            double candidate = ideal + offset;
            int clear = safeSegments(world, ship, candidate);
            if (clear == 0) continue;
            // Prefer proven open-water headings over a dangerously short
            // forward corridor, even when the safer route requires turning.
            double score = 55 * clear - 0.28 * Math.abs(offset) -
                0.06 * Math.abs(MathHelper.wrapDegrees(candidate - ship.getYaw()));
            if (clear == 1) score -= 30;
            // Bias to keep a sensible course rather than oscillating.
            if (!Double.isNaN(memory.chosenHeading)) {
                score -= 0.13 * Math.abs(MathHelper.wrapDegrees(
                    candidate - memory.chosenHeading));
            }
            // When effectively stuck, a different direction is preferable.
            if (memory.stuckChecks >= 2 && Math.abs(offset) < 30) score -= 25;
            if (score > bestScore) {
                bestScore = score;
                choice = candidate;
            }
        }
        if (bestScore <= -1e8) {
            // No verified water ahead. Turn under minimum sail instead of
            // furling everything. Do not probe or force-load new chunks.
            return ship.getYaw() + 120;
        }
        return choice;
    }

    private static int safeSegments(ServerWorld world, Ship ship, double heading) {
        double radians = Math.toRadians(heading);
        double forwardX = -Math.sin(radians), forwardZ = Math.cos(radians);
        double rightX = forwardZ, rightZ = -forwardX;
        int seaY = world.getSeaLevel() - 1;
        int count = 0;
        for (int step = 1; step <= 3; step++) {
            double distance = step * 12.0;
            boolean passable = true;
            for (double side : new double[]{-3.0, 0.0, 3.0}) {
                double x = ship.getX() + forwardX * distance + rightX * side;
                double z = ship.getZ() + forwardZ * distance + rightZ * side;
                BlockPos water = BlockPos.ofFloored(x, seaY, z);
                if (!world.isChunkLoaded(water) ||
                    !world.getFluidState(water).isOf(Fluids.WATER) ||
                    !world.getFluidState(water.down()).isOf(Fluids.WATER)) {
                    passable = false;
                    break;
                }
            }
            if (!passable) break;
            count++;
        }
        return count;
    }

    private static double horizontalSq(Vec3d a, Vec3d b) {
        double x = a.x - b.x, z = a.z - b.z;
        return x * x + z * z;
    }
}
