package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real server boss bars: at most two ship bars per player, update once a second. */
public final class PirateBossBars {
    private static final Map<UUID,ServerBossBar> BARS=new HashMap<>();
    private PirateBossBars(){}
    public static void tick(ServerWorld world){
        if(world.getTime()%20!=0)return;
        if(!PirateSettings.ACTIVE.bossBarsEnabled){
            if(!BARS.isEmpty())reset();
            return;
        }
        List<Ship> ships=PirateShips.loaded(world);
        Map<UUID,Set<UUID>> visible=new HashMap<>();
        for(ServerPlayerEntity player:world.getPlayers()){
            if(!player.isAlive()||player.isSpectator())continue;
            List<Ship> candidates=new ArrayList<>();
            for(Ship ship:ships){
                if(!PirateShips.engaged(ship) || ship.isSunken() || !ship.isAlive())
                    continue;
                if(player.squaredDistanceTo(ship)>96*96)continue;
                candidates.add(ship);
            }
            candidates.sort((a,b)->{
                boolean fa=PirateShips.isFlagship(a),fb=PirateShips.isFlagship(b);
                if(fa!=fb)return fa?-1:1;
                return Double.compare(player.squaredDistanceTo(a),player.squaredDistanceTo(b));
            });
            for(int i=0;i<Math.min(2,candidates.size());i++){
                Ship ship=candidates.get(i);
                visible.computeIfAbsent(ship.getUuid(),k->new HashSet<>())
                    .add(player.getUuid());
            }
        }

        for(Ship ship:ships){
            UUID id=ship.getUuid();
            Set<UUID> viewers=visible.getOrDefault(id,Set.of());
            if(viewers.isEmpty()&&!BARS.containsKey(id))continue;
            ServerBossBar bar=BARS.computeIfAbsent(id,ignored->new ServerBossBar(
                Text.literal(title(ship)),
                PirateShips.isFlagship(ship)?BossBar.Color.PURPLE:BossBar.Color.RED,
                BossBar.Style.NOTCHED_10));
            bar.setName(Text.literal(title(ship)));
            float max=Math.max(1,ship.getAttributes().maxHealth);
            bar.setPercent(ship.isSinking()?0:
                MathHelper.clamp(1f-ship.getDamageWobbleStrength()/max,0f,1f));
            for(ServerPlayerEntity p:List.copyOf(bar.getPlayers()))
                if(!viewers.contains(p.getUuid()))bar.removePlayer(p);
            for(ServerPlayerEntity p:world.getPlayers())
                if(viewers.contains(p.getUuid())&&!bar.getPlayers().contains(p))
                    bar.addPlayer(p);
            if(bar.getPlayers().isEmpty())BARS.remove(id);
        }
    }
    public static String title(Ship ship){
        PirateShipRoster.Hull hull=PirateShipRoster.hullOf(ship);
        String state=PirateCombat.mode(ship);
        String suffix=state.equals("repairing")?" - Repairing":
            state.equals("repair withdrawal")?" - Withdrawing":
            state.equals("full retreat")?" - Retreating":
            state.equals("transferring illager troops")?" - Reinforcing Fleet":"";
        if(PirateShips.isFlagship(ship))return "Admiral's Galleon - Fleet Flagship"+suffix;
        return "Illager "+hull.display+suffix;
    }
    public static void remove(Ship ship){
        ServerBossBar bar=BARS.remove(ship.getUuid());
        if(bar!=null)bar.clearPlayers();
    }
    public static void reset(){
        for(ServerBossBar bar:BARS.values())bar.clearPlayers();
        BARS.clear();
    }
}
