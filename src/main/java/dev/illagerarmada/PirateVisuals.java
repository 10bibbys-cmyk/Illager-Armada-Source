package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/** Native Small Ships sail dyes and the ominous Pillager banner design. */
public final class PirateVisuals {
    private PirateVisuals(){}

    public static void apply(Ship ship,PirateShipRoster.Hull hull){
        if(!ship.getCommandTags().add("ia_colours"))return;
        if(!PirateSettings.ACTIVE.pirateSailsEnabled)return;
        int personality=Math.floorMod(ship.getUuid().hashCode(),4);
        String color=switch(hull){
            case GALLEY -> new String[]{"red","black","brown","gray"}[personality];
            case DRAKKAR -> new String[]{"red","brown","orange","black"}[personality];
            case DHOW -> new String[]{"brown","orange","gray","black"}[personality];
            case CARAVEL -> new String[]{"gray","black","purple","red"}[personality];
            case COG -> new String[]{"black","gray","brown","purple"}[personality];
            case BRIGG -> new String[]{"red","black","purple","gray"}[personality];
            case GALLEON -> new String[]{"black","purple","red","gray"}[personality];
        };
        int accent=switch(color){
            case "red" -> 14;
            case "purple" -> 10;
            case "brown" -> 12;
            case "orange" -> 1;
            case "gray" -> 7;
            default -> 9; // cyan against black canvas
        };
        // Vanilla ominous/Illager banner motif, rendered using native banner layers.
        ItemStack banner=new ItemStack(Items.WHITE_BANNER);
        NbtList patterns=new NbtList();
        pattern(patterns,"mr",accent);
        pattern(patterns,"bs",8);
        pattern(patterns,"cs",7);
        pattern(patterns,"bo",8);
        pattern(patterns,"ms",15);
        pattern(patterns,"hh",8);
        pattern(patterns,"mc",8);
        pattern(patterns,"bo",15);
        banner.getOrCreateSubNbt("BlockEntityTag").put("Patterns",patterns);
        ship.setData(Ship.SAIL_COLOR,color);
        ship.setData(Ship.BANNER,banner.copy());
        ship.setData(Ship.SAIL_BANNER,banner.copy());
    }
    private static void pattern(NbtList list,String id,int color){
        NbtCompound layer=new NbtCompound();
        layer.putString("Pattern",id);
        layer.putInt("Color",color);
        list.add(layer);
    }
}
