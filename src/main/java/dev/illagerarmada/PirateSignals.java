package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One-shot goat-horn cues. Nothing is polled each tick. */
public final class PirateSignals {
    public enum Signal {
        SIGHTED(240), RAM(400), RETREAT(500),
        REPAIR(180), STRAFE(360), BROADSIDE(340), RALLY(450),
        INTIMIDATE(180);
        final int delay;
        Signal(int delay){this.delay=delay;}
    }
    private static final Map<UUID,EnumMap<Signal,Long>> LAST=new HashMap<>();
    private static final Map<UUID,Long> NEXT_TAUNT=new HashMap<>();
    private PirateSignals(){}

    private static SoundEvent sound(String name, String fallback){
        String selected=PirateSettings.ACTIVE.battleHornSounds.getOrDefault(name,fallback);
        if(selected==null || selected.equalsIgnoreCase("none"))return null;
        try{
            Identifier id=new Identifier(selected);
            return Registries.SOUND_EVENT.getOrEmpty(id).orElse(null);
        }catch(IllegalArgumentException ex){return null;}
    }
    public static void emit(ServerWorld world,Ship ship,Signal signal){
        if(!PirateSettings.ACTIVE.battleHornsEnabled)return;
        long now=world.getTime();
        EnumMap<Signal,Long> memory=LAST.computeIfAbsent(ship.getUuid(),
            k->new EnumMap<>(Signal.class));
        if(now<memory.getOrDefault(signal,Long.MIN_VALUE/2)+signal.delay)return;
        memory.put(signal,now);
        SoundEvent event=sound(signal.name().toLowerCase(java.util.Locale.ROOT),
            "minecraft:item.goat_horn.sound.0");
        if(event==null)return;
        world.playSound(null,ship.getBlockPos(),event,
            SoundCategory.HOSTILE,PirateSettings.ACTIVE.battleHornVolume,.98f);
    }
    public static void forget(Ship ship){
        LAST.remove(ship.getUuid());
        NEXT_TAUNT.remove(ship.getUuid());
    }
    public static void reset(){LAST.clear();NEXT_TAUNT.clear();}

    /** Rare, staggered intimidation calls while ships are actively engaged.
     * No entity scans; caller checks at most once per second.
     */
    public static void battlePresence(ServerWorld world,Ship ship){
        if(!PirateSettings.ACTIVE.battleHornsEnabled ||
           !PirateSettings.ACTIVE.intimidationHornsEnabled)return;
        long now=world.getTime();
        UUID id=ship.getUuid();
        Long next=NEXT_TAUNT.get(id);
        if(next==null){
            NEXT_TAUNT.put(id,now+60+Math.floorMod(id.hashCode(),120));
            return;
        }
        if(now<next)return;
        emit(world,ship,Signal.INTIMIDATE);
        int min=PirateSettings.ACTIVE.intimidationHornMinTicks;
        int max=PirateSettings.ACTIVE.intimidationHornMaxTicks;
        int variance=Math.max(0,max-min);
        long phase=now/20+id.getLeastSignificantBits();
        int extra=variance==0?0:(int)Math.floorMod(phase^(phase>>>17),variance+1);
        int interval=min+extra;
        // Fleetwide calls should be audible, but four horns never fire at once.
        if(PirateFleet.isFleetShip(ship) && !PirateFleet.isFlagship(ship))
            interval+=(interval/2);
        NEXT_TAUNT.put(id,now+interval);
    }

    public static void encounter(ServerWorld world, List<ServerPlayerEntity> crew,
                                 Vec3d spawn, String name, boolean fleet){
        if(!PirateSettings.ACTIVE.encounterChatEnabled &&
           !PirateSettings.ACTIVE.encounterSoundEnabled)return;
        for(ServerPlayerEntity player:crew){
            if(!player.isAlive() || player.isRemoved())continue;
            double dx=spawn.x-player.getX(),dz=spawn.z-player.getZ();
            String[] directions={"North","Northeast","East","Southeast",
                "South","Southwest","West","Northwest"};
            int point=Math.floorMod((int)Math.round(Math.atan2(dx,-dz)/
                (Math.PI/4)),8);
            String message="Black sails sighted to the "+directions[point]+
                "! "+(fleet?"An Illager fleet":"Illager "+name)+".";
            if(PirateSettings.ACTIVE.encounterChatEnabled)
                player.sendMessage(Text.literal(message).formatted(Formatting.GOLD),false);
        }
        // One horn for the entire vessel group; don't stack duplicate sounds
        // on multiplayer crews standing on the same deck.
        if(PirateSettings.ACTIVE.encounterSoundEnabled && !crew.isEmpty()){
            ServerPlayerEntity first=crew.get(0);
            SoundEvent notice=sound("sighted","minecraft:item.goat_horn.sound.2");
            if(notice!=null)world.playSound(null,first.getBlockPos(),notice,
                SoundCategory.HOSTILE,1.2f,.9f);
        }
    }
}
