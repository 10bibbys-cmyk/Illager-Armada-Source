package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Transactional four-ship fleet: flagship, defender, two independent attackers. */
public final class PirateFleet {
    public static final String FLEET="ia_fleet_", LEADER="ia_leader_";
    public static final String FLAGSHIP="ia_role_flagship";
    public static final String GUARD="ia_role_guard";
    public static final String ATTACKER="ia_role_attacker";
    private PirateFleet(){}

    public static boolean isFleetShip(Ship ship) {
        return ship.getCommandTags().stream().anyMatch(t->t.startsWith(FLEET));
    }
    public static boolean isFlagship(Ship ship) {
        return ship.getCommandTags().contains(FLAGSHIP);
    }
    public static boolean isGuard(Ship ship) {
        return ship.getCommandTags().contains(GUARD);
    }
    public static boolean isAttacker(Ship ship) {
        return ship.getCommandTags().contains(ATTACKER);
    }
    public static UUID group(Ship ship) {
        return uuidTag(ship,FLEET);
    }
    public static UUID leaderId(Ship ship){
        if(isFlagship(ship))return ship.getUuid();
        return uuidTag(ship,LEADER);
    }
    private static UUID uuidTag(Ship ship,String prefix){
        for(String tag:ship.getCommandTags())if(tag.startsWith(prefix)){
            try{return UUID.fromString(tag.substring(prefix.length()));}
            catch(IllegalArgumentException ignored){}
        }
        return null;
    }

    public static Ship flagship(ServerWorld world,Ship member) {
        UUID leader=leaderId(member);
        return leader==null?null:PirateShips.find(world,leader);
    }

    public static int fleetCount(ServerWorld world) {
        int count=0;
        for(Ship ship:PirateShips.loaded(world))if(isFlagship(ship) &&
            !ship.isSinking()&&!ship.isSunken())count++;
        return count;
    }

    public static Ship spawn(ServerWorld world,BlockPos center,float yaw) {
        if(PirateShips.activeCount(world)+4>PirateSettings.ACTIVE.maxPiratesPerDimension)
            return null;
        if(fleetCount(world)>=PirateSettings.ACTIVE.maxFleetsPerDimension)return null;
        Random rng=new Random(world.random.nextLong());
        PirateShipRoster.Hull guard=rng.nextBoolean()?
            PirateShipRoster.Hull.CARAVEL:PirateShipRoster.Hull.BRIGG;
        PirateShipRoster.Hull attackerA=rng.nextBoolean()?
            PirateShipRoster.Hull.GALLEY:PirateShipRoster.Hull.CARAVEL;
        // Every fleet carries its own finite troop transport.
        PirateShipRoster.Hull attackerB=PirateShipRoster.Hull.DRAKKAR;
        PirateShipRoster.Hull[] roster={
            PirateShipRoster.Hull.GALLEON,guard,attackerA,attackerB};
        // Space ships well beyond hull size and ensure every chunk is loaded.
        // All spawn sites are prevalidated BEFORE spawning any entity.
        double a=Math.toRadians(yaw);
        Vec3d forward=new Vec3d(-Math.sin(a),0,Math.cos(a));
        Vec3d right=new Vec3d(forward.z,0,-forward.x);
        Vec3d[] offsets={
            Vec3d.ZERO,
            right.multiply(27).add(forward.multiply(12)),
            right.multiply(-36).add(forward.multiply(40)),
            right.multiply(37).add(forward.multiply(45))
        };
        BlockPos[] locations=new BlockPos[4];
        for(int i=0;i<4;i++){
            locations[i]=BlockPos.ofFloored(center.getX()+offsets[i].x,
                center.getY(),center.getZ()+offsets[i].z);
            if(!PirateShips.validWater(world,locations[i],roster[i]))return null;
        }

        UUID group=UUID.randomUUID();
        List<Ship> created=new ArrayList<>();
        Ship flagship=null;
        for(int i=0;i<4;i++){
            String role=i==0?FLAGSHIP:i==1?GUARD:ATTACKER;
            Ship ship=PirateShips.spawn(world,locations[i],yaw,roster[i],
                PirateShipRoster.randomWood(rng),group,role,
                flagship==null?null:flagship.getUuid());
            if(ship==null){
                for(Ship partial:created)PirateShips.clear(world,partial,true);
                return null;
            }
            created.add(ship);
            if(i==0)flagship=ship;
        }
        return flagship;
    }
}
