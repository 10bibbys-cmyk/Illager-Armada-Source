package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.*;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Real Small Ships 2.0.2 hulls, with their native seats, physics and batteries. */
public final class PirateShipRoster {
    private PirateShipRoster() {}

    public enum Hull {
        GALLEY("Galley","Rammer",22,2,1,1,1,5,6),
        DRAKKAR("Drakkar","Fleet Troop Carrier",12,0,2,3,9,5,7),
        DHOW("Dhow","Opportunist",17,4,1,1,2,5,6),
        CARAVEL("Caravel","Duelist",24,4,1,1,3,5,7),
        COG("Cog","Bruiser",13,4,0,0,2,6,7),
        BRIGG("Brigg","Hunter",12,6,1,2,3,7,8),
        GALLEON("Galleon","Fleet Flagship",0,10,1,2,5,10,11);

        public final String display, role;
        public final int weight, cannons, minLaunches, maxLaunches, reserveGoal, depth, clearance;
        Hull(String display,String role,int weight,int cannons,int minLaunches,
             int maxLaunches,int reserveGoal,int depth,int clearance) {
            this.display=display;this.role=role;this.weight=weight;this.cannons=cannons;
            this.minLaunches=minLaunches;this.maxLaunches=maxLaunches;
            this.reserveGoal=reserveGoal;this.depth=depth;this.clearance=clearance;
        }
        public static Hull choose(Random random) {
            int sum=0;
            for(Hull h:values())
                if(h!=DRAKKAR || !PirateSettings.ACTIVE.drakkarFleetOnly)
                    sum+=h.weight;
            int roll=random.nextInt(sum);
            for(Hull h:values()){
                if(h==DRAKKAR && PirateSettings.ACTIVE.drakkarFleetOnly)continue;
                if((roll-=h.weight)<0)return h;
            }
            return CARAVEL;
        }
        public static Hull parse(String id) {
            try{return valueOf(id.toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException e){return null;}
        }
    }

    private static final BoatEntity.Type[] WOODS = {
        BoatEntity.Type.OAK,BoatEntity.Type.SPRUCE,BoatEntity.Type.BIRCH,
        BoatEntity.Type.JUNGLE,BoatEntity.Type.ACACIA,BoatEntity.Type.DARK_OAK,
        BoatEntity.Type.MANGROVE,BoatEntity.Type.CHERRY,BoatEntity.Type.BAMBOO
    };
    public static BoatEntity.Type randomWood(Random random) {
        return WOODS[random.nextInt(WOODS.length)];
    }
    public static List<String> woodNames() {
        List<String> woods=new ArrayList<>();
        for(BoatEntity.Type type:WOODS)woods.add(type.getName());
        return woods;
    }

    public static Ship summon(ServerWorld world,Hull hull,double x,double y,double z,
                              BoatEntity.Type wood,float yaw) {
        Ship ship=switch(hull) {
            case GALLEY -> GalleyEntity.summon(world,x,y,z);
            case DRAKKAR -> DrakkarEntity.summon(world,x,y,z);
            case DHOW -> DhowEntity.summon(world,x,y,z);
            case CARAVEL -> CaravelEntity.summon(world,x,y,z);
            case COG -> CogEntity.summon(world,x,y,z);
            case BRIGG -> BriggEntity.summon(world,x,y,z);
            case GALLEON -> GalleonEntity.summon(world,x,y,z);
        };
        ship.setYaw(yaw);
        ship.setVariant(wood);
        return ship;
    }

    public static Hull hullOf(Ship ship) {
        if(ship instanceof GalleonEntity)return Hull.GALLEON;
        if(ship instanceof BriggEntity)return Hull.BRIGG;
        if(ship instanceof CogEntity)return Hull.COG;
        if(ship instanceof DrakkarEntity)return Hull.DRAKKAR;
        if(ship instanceof GalleyEntity)return Hull.GALLEY;
        if(ship instanceof DhowEntity)return Hull.DHOW;
        return Hull.CARAVEL;
    }
    public static boolean hasCannons(Ship ship) { return ship instanceof Cannonable; }
}
