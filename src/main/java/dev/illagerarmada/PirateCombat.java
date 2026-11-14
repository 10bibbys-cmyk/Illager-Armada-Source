package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captain decisions and engineer repairs. Executed at the ship-controller's
 * bounded five-tick interval; threat assessments run every two seconds.
 * Kit count is stored on the vessel for save/reload safety.
 */
public final class PirateCombat {
    private static final String KIT_TAG="ia_repairs_", MORALE_TAG="ia_morale_", ESCAPE_TAG="ia_escape_until_";
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final int REPAIR_TICKS=100, MAX_KITS=3;
    private PirateCombat(){}

    private static final class State {
        int initialCrew, lastCrew, morale=65;
        float lastDamage;
        long decideAt, cooldownUntil, repairingUntil, escapeUntil, withdrawSince;
        boolean withdrawing, escaping;
        String mode="combat";
    }
    public record Directive(Vec3d destination,int sails,String mode,boolean stop) {}

    public static void forget(Ship ship){STATES.remove(ship.getUuid());}
    public static void reset(){STATES.clear();}

    public static int kits(Ship ship){
        for(String t:ship.getCommandTags())if(t.startsWith(KIT_TAG)){
            try{return MathHelper.clamp(Integer.parseInt(t.substring(KIT_TAG.length())),0,MAX_KITS);}
            catch(NumberFormatException ignored){}
        }
        ship.addCommandTag(KIT_TAG+MAX_KITS);
        return MAX_KITS;
    }
    private static void setKits(Ship ship,int amount){
        for(String tag:java.util.List.copyOf(ship.getCommandTags()))
            if(tag.startsWith(KIT_TAG))ship.removeScoreboardTag(tag);
        ship.addCommandTag(KIT_TAG+MathHelper.clamp(amount,0,MAX_KITS));
    }
    public static String mode(Ship ship){
        State state=STATES.get(ship.getUuid());
        return state==null?"combat":state.mode;
    }
    public static String status(Ship ship){
        State s=STATES.get(ship.getUuid());
        return "kits="+kits(ship)+" morale="+(s==null?"--":s.morale)+
            " order="+(s==null?"combat":s.mode);
    }

    public static Directive tick(ServerWorld world,Ship ship,PirateTactics.Target target){
        State s=STATES.computeIfAbsent(ship.getUuid(),id->new State());
        long now=world.getTime();
        int crew=ship.getPassengerList().size();
        float max=Math.max(1,ship.getAttributes().maxHealth);
        float damage=ship.getDamageWobbleStrength();
        int confidence=PirateTactics.confidence(ship);
        if(s.initialCrew==0){
            s.initialCrew=Math.max(1,crew);
            s.lastCrew=crew;
            s.lastDamage=damage;
            s.morale=MathHelper.clamp(50+confidence/4,20,95);
            for(String tag:ship.getCommandTags()){
                if(tag.startsWith(MORALE_TAG))try{
                    s.morale=MathHelper.clamp(Integer.parseInt(
                        tag.substring(MORALE_TAG.length())),0,100);
                }catch(NumberFormatException ignored){}
                if(tag.startsWith(ESCAPE_TAG))try{
                    s.escapeUntil=Long.parseLong(tag.substring(ESCAPE_TAG.length()));
                    s.escaping=now<s.escapeUntil;
                }catch(NumberFormatException ignored){}
            }
        }
        // Morale reacts to casualties and damage. No new entity queries.
        if(crew<s.lastCrew)s.morale=Math.max(0,s.morale-(s.lastCrew-crew)*12);
        if(damage>s.lastDamage+Math.max(2,max*.035f))
            s.morale=Math.max(0,s.morale-5);
        s.lastCrew=crew;s.lastDamage=damage;
        if(now%80==0){
            for(String tag:java.util.List.copyOf(ship.getCommandTags()))
                if(tag.startsWith(MORALE_TAG))ship.removeScoreboardTag(tag);
            ship.addCommandTag(MORALE_TAG+s.morale);
        }

        if(s.repairingUntil>0){
            // A genuine colliding, moving enemy vessel cancels repair without healing.
            if(rammedByEnemy(world,ship)){
                s.repairingUntil=0;
                s.withdrawing=false;
                s.cooldownUntil=now+140;
                s.mode="repair interrupted";
                world.playSound(null,ship.getBlockPos(),SoundEvents.BLOCK_ANVIL_LAND,SoundCategory.BLOCKS,.8f,.7f);
                return null;
            }
            if(PirateCrew.occupant(ship,PirateCrew.ENGINEER)==null){
                s.repairingUntil=0;
                s.withdrawing=false;
                s.cooldownUntil=now+140;
                s.mode="engineer lost";
                return null;
            }
            if(now>=s.repairingUntil){
                ship.setDamageWobbleStrength(Math.max(0,
                    ship.getDamageWobbleStrength()-ship.getAttributes().maxHealth*.25f));
                s.repairingUntil=0;
                s.withdrawing=false;
                s.cooldownUntil=now+180;
                s.morale=Math.min(100,s.morale+12);
                s.mode="repair complete";
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    ship.getX(),ship.getY()+2,ship.getZ(),16,1.8,.7,1.8,.06);
                world.playSound(null,ship.getBlockPos(),SoundEvents.BLOCK_WOOD_PLACE,SoundCategory.BLOCKS,1f,.9f);
                return null;
            }
            s.mode="repairing";
            if(now%20==0)world.spawnParticles(ParticleTypes.POOF,
                ship.getX(),ship.getY()+2,ship.getZ(),6,1,.4,1,.025);
            PirateNavigation.stop(ship);
            return new Directive(null,0,s.mode,true);
        }

        if(s.withdrawing){
            if(PirateCrew.occupant(ship,PirateCrew.ENGINEER)==null ||
               kits(ship)==0){
                s.withdrawing=false;s.cooldownUntil=now+120;
            }else if(target==null || target.distanceSq()>=48*48 ||
                    now-s.withdrawSince>240 && target.distanceSq()>=34*34){
                setKits(ship,kits(ship)-1); // interrupting costs a real kit
                s.repairingUntil=now+REPAIR_TICKS;
                s.withdrawing=false;
                s.mode="repairing";
                PirateSignals.emit(world,ship,PirateSignals.Signal.REPAIR);
                PirateNavigation.stop(ship);
                return new Directive(null,0,s.mode,true);
            }else{
                s.mode="repair withdrawal";
                return new Directive(away(ship,target.vessel(),85),4,s.mode,false);
            }
        }

        // Keep the escape order for at least fifteen seconds even after
        // the target leaves the 100-block acquisition radius.
        if(s.escaping && now>=s.escapeUntil &&
           (target==null || target.distanceSq()>125*125)){
            s.escaping=false;s.mode="escaped";
            for(String tag:java.util.List.copyOf(ship.getCommandTags()))
                if(tag.startsWith(ESCAPE_TAG))ship.removeScoreboardTag(tag);
        }
        if(s.escaping){
            s.mode="full retreat";
            if(target!=null)return new Directive(away(ship,target.vessel(),145),4,s.mode,false);
            return new Directive(ship.getPos().add(forward(ship).multiply(100)),4,s.mode,false);
        }
        if(target==null)return null;
        if(now<s.decideAt)return null;
        s.decideAt=now+40;

        float fraction=MathHelper.clamp(damage/max,0f,1f);
        float enemyDamage=0;
        if(target.vessel() instanceof Ship enemy)
            enemyDamage=MathHelper.clamp(enemy.getDamageWobbleStrength()/
                Math.max(1,enemy.getAttributes().maxHealth),0f,1f);
        int missing=Math.max(0,s.initialCrew-crew);
        boolean engineer=PirateCrew.occupant(ship,PirateCrew.ENGINEER)!=null;
        double risk=fraction*58+((double)missing/s.initialCrew)*30+
            Math.max(0,target.crew()-crew)*2.0-target.cargo()*7+
            PirateTactics.cargoOf(ship)*14-enemyDamage*29-
            (confidence-50)*.34-(s.morale-50)*.24;
        UUID group=PirateFleet.group(ship);
        if(group!=null){
            int allies=0;
            for(Ship ally:PirateShips.loaded(world))
                if(ally!=ship && group.equals(PirateFleet.group(ally)) &&
                   ally.isAlive() && !ally.isSinking() && !ally.isSunken())allies++;
            risk-=allies*5;
            Ship flagship=PirateFleet.flagship(world,ship);
            if(flagship!=null && flagship!=ship && (flagship.isSinking() || flagship.isSunken()))
                risk+=20;
        }
        if(!engineer || kits(ship)==0)risk+=9;
        // Prefer trying a kit to permanently fleeing, if the engineer survives.
        // A badly outmatched, cautious captain may abandon the vessel even
        // when repair supplies remain.
        if(risk>63 && fraction>.65f && confidence<45){
            startEscape(world,ship,s,now);
            s.mode="full retreat";
            return new Directive(away(ship,target.vessel(),145),4,s.mode,false);
        }
        if(fraction>=.55f && engineer && kits(ship)>0 && now>=s.cooldownUntil &&
           (fraction>.7f || risk>18)) {
            s.withdrawing=true;s.withdrawSince=now;
            s.mode="repair withdrawal";
            return new Directive(away(ship,target.vessel(),85),4,s.mode,false);
        }
        if(risk>32 && (fraction>.55f || missing>s.initialCrew/3 ||
                       s.morale<30)){
            startEscape(world,ship,s,now);
            s.mode="full retreat";
            return new Directive(away(ship,target.vessel(),145),4,s.mode,false);
        }
        // Morale recovers slowly while the crew holds its ground.
        if(s.morale<85 && fraction<.45f)s.morale++;
        return null;
    }

    private static void startEscape(ServerWorld world,Ship ship,State state,long now){
        state.escaping=true;
        state.escapeUntil=now+300;
        for(String tag:java.util.List.copyOf(ship.getCommandTags()))
            if(tag.startsWith(ESCAPE_TAG))ship.removeScoreboardTag(tag);
        ship.addCommandTag(ESCAPE_TAG+state.escapeUntil);
        PirateSignals.emit(world,ship,PirateSignals.Signal.RETREAT);
    }
    /** Repair collision checks are local and run only while repairing. */
    private static boolean rammedByEnemy(ServerWorld world,Ship ship){
        for(net.minecraft.entity.vehicle.BoatEntity other:world.getEntitiesByClass(
            net.minecraft.entity.vehicle.BoatEntity.class,
            ship.getBoundingBox().expand(4),boat->boat!=ship &&
                boat.isAlive() && !boat.getCommandTags().contains(IllagerArmada.SHIP))){
            boolean crewed=other.getPassengerList().stream().anyMatch(
                rider->rider instanceof net.minecraft.server.network.ServerPlayerEntity);
            if(other instanceof Ship target && target.getHelmsman()
                instanceof net.minecraft.server.network.ServerPlayerEntity)
                crewed=true;
            if(crewed && ramming(ship,other))return true;
        }
        return false;
    }
    private static boolean ramming(Ship ship,Entity enemy){
        if(!enemy.isAlive() || enemy.isRemoved())return false;
        Vec3d relative=enemy.getVelocity().subtract(ship.getVelocity());
        return relative.horizontalLengthSquared()>.015 &&
            ship.getBoundingBox().expand(2).intersects(enemy.getBoundingBox().expand(1));
    }
    private static Vec3d away(Ship ship,Entity enemy,double distance){
        Vec3d delta=ship.getPos().subtract(enemy.getPos());
        delta=new Vec3d(delta.x,0,delta.z);
        if(delta.horizontalLengthSquared()<1)delta=forward(ship);
        return ship.getPos().add(delta.normalize().multiply(distance));
    }
    private static Vec3d forward(Ship ship){
        double rad=Math.toRadians(ship.getYaw());
        return new Vec3d(-Math.sin(rad),0,Math.cos(rad));
    }
}
