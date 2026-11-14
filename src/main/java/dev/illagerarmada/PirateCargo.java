package dev.illagerarmada;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.talhanation.smallships.world.entity.ship.ContainerShip;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.item.CannonBallItem;
import com.talhanation.smallships.world.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.Reader;
import java.util.Random;

/**
 * One-time cargo manifest, stored in Small Ships' real persistent hold.
 * Packs can replace data/illager_armada/pirate_cargo/<hull>.json through a
 * standard datapack or KubeJS. No loot is rerolled on inventory access.
 */
public final class PirateCargo {
    private static final String DONE="ia_provisioned";
    private static final String WRECK="ia_ammo_salvaged";
    private PirateCargo(){}

    public static void provision(ServerWorld world, Ship ship, PirateShipRoster.Hull hull) {
        if(!(ship instanceof ContainerShip hold) || !ship.getCommandTags().add(DONE))return;
        Random random=new Random(world.random.nextLong()^ship.getUuid().getLeastSignificantBits());
        // The ammunition budget comes first; loot never starves the guns.
        int gunCount=ship instanceof Cannonable battery ? battery.getCannons().size():0;
        if(gunCount>0){
            int low=PirateSettings.ACTIVE.ammoPerCannonMin;
            int high=PirateSettings.ACTIVE.ammoPerCannonMax;
            int count=gunCount*(low+random.nextInt(high-low+1));
            int ball=(int)Math.round(count*(.76+random.nextDouble()*.08));
            int chain=(int)Math.round((count-ball)*(.50+random.nextDouble()*.18));
            put(hold,new ItemStack(ModItems.CANNON_BALL,ball));
            put(hold,new ItemStack(ModItems.CHAINED_SHOT,chain));
            put(hold,new ItemStack(ModItems.GRAPE_SHOT,count-ball-chain));
        }
        int startingCrew=ship.getPassengerList().size();
        Identifier path=new Identifier("illager_armada","pirate_cargo/"+
            hull.name().toLowerCase(java.util.Locale.ROOT)+".json");
        try {
            var resource=world.getServer().getResourceManager().getResource(path);
            if(resource.isEmpty()){
                System.err.println("[Illager Armada] Missing cargo manifest: "+path);
            }else try(Reader reader=resource.get().getReader()){
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray pools=root.getAsJsonArray("pools");
                if(pools!=null){
                    for(JsonElement p:pools){
                        if(!p.isJsonObject())continue;
                        JsonObject pool=p.getAsJsonObject();
                        JsonArray entries=pool.getAsJsonArray("entries");
                        if(entries==null || entries.isEmpty())continue;
                        int min=field(pool,"rolls_min",1),max=field(pool,"rolls_max",min);
                        int rolls=min+random.nextInt(Math.max(1,max-min+1));
                        if(pool.has("crew_bonus") && pool.get("crew_bonus").getAsBoolean())
                            rolls+=Math.min(3,Math.max(0,(startingCrew-3)/4));
                        rolls=MathHelper.clamp((int)Math.ceil(rolls*
                            PirateSettings.ACTIVE.cargoRollMultiplier),0,32);
                        for(int i=0;i<rolls;i++)roll(hold,entries,random);
                    }
                }
            }
        }catch(Exception ex){
            System.err.println("[Illager Armada] Invalid cargo manifest "+path+": "+ex.getMessage());
        }
        hold.markDirty();
    }

    private static int field(JsonObject obj,String key,int fallback){
        try{return obj.has(key)?obj.get(key).getAsInt():fallback;}
        catch(RuntimeException ex){return fallback;}
    }
    private static void roll(ContainerShip hold,JsonArray entries,Random random){
        int weight=0;
        for(JsonElement e:entries)if(e.isJsonObject())
            weight+=Math.max(0,field(e.getAsJsonObject(),"weight",1));
        if(weight<=0)return;
        int choice=random.nextInt(weight);
        for(JsonElement e:entries){
            if(!e.isJsonObject())continue;
            JsonObject item=e.getAsJsonObject();
            choice-=Math.max(0,field(item,"weight",1));
            if(choice>=0)continue;
            try{
                Identifier id=new Identifier(item.get("item").getAsString());
                Item value=Registries.ITEM.getOrEmpty(id).orElse(Items.AIR);
                if(value==Items.AIR)return;
                int min=Math.max(1,field(item,"min",1));
                int max=Math.max(min,field(item,"max",min));
                int amount=Math.min(256,min+random.nextInt(Math.max(1,max-min+1)));
                put(hold,new ItemStack(value,amount));
            }catch(Exception ignored){}
            return;
        }
    }

    public static void put(ContainerShip hold,ItemStack input){
        int remaining=input.getCount();
        if(remaining<=0)return;
        for(int i=0;i<hold.size() && remaining>0;i++){
            ItemStack old=hold.getStack(i);
            if(old.isEmpty() || !old.isOf(input.getItem()) ||
               old.getNbt()!=null || input.getNbt()!=null)continue;
            int n=Math.min(remaining,old.getMaxCount()-old.getCount());
            if(n<=0)continue;
            old.increment(n);remaining-=n;
        }
        for(int i=0;i<hold.size() && remaining>0;i++){
            if(!hold.getStack(i).isEmpty())continue;
            int n=Math.min(remaining,input.getMaxCount());
            hold.setStack(i,new ItemStack(input.getItem(),n));
            remaining-=n;
        }
        hold.markDirty();
    }

    /** Keep approximately 25% of unused cannon ammunition, preserving types. */
    public static void salvage(Ship ship){
        if(!(ship instanceof ContainerShip hold) || !ship.getCommandTags().add(WRECK))return;
        float fraction=PirateSettings.ACTIVE.ammoSalvageFraction;
        Random rng=new Random(ship.getUuid().getMostSignificantBits()^0xA55A1B1L);
        for(int i=0;i<hold.size();i++){
            ItemStack item=hold.getStack(i);
            if(item.isEmpty())continue;
            if(item.getItem() instanceof CannonBallItem){
                double expected=item.getCount()*fraction;
                int retained=(int)Math.floor(expected);
                if(rng.nextDouble()<(expected-retained))retained++;
                if(retained==0)hold.setStack(i,ItemStack.EMPTY);
                else item.setCount(retained);
            }else if(item.isOf(ModItems.FINE_GRAIN_POWDER)){
                hold.setStack(i,ItemStack.EMPTY);
            }
        }
        hold.markDirty();
    }
}
