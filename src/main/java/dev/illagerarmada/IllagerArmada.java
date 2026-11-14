package dev.illagerarmada;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-only ship controllers and operator diagnostics. */
public final class IllagerArmada implements ModInitializer {
    public static final String SHIP="pirate_ship", ILLAGER="pirate_illager";

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,env)->{
            dispatcher.register(literal("illagerarmada").requires(s->s.hasPermissionLevel(2))
                .then(literal("summon").then(argument("pos",BlockPosArgumentType.blockPos())
                    .executes(c->summon(c.getSource(),
                        BlockPosArgumentType.getLoadedBlockPos(c,"pos"),
                        PirateShipRoster.Hull.CARAVEL))))
                .then(literal("summonship").then(argument("type",StringArgumentType.word())
                    .suggests((c,b)->{
                        for(PirateShipRoster.Hull hull:PirateShipRoster.Hull.values())
                            if(hull!=PirateShipRoster.Hull.GALLEON)
                                b.suggest(hull.name().toLowerCase(java.util.Locale.ROOT));
                        return b.buildFuture();
                    }).executes(c->summonNear(c.getSource(),
                        StringArgumentType.getString(c,"type")))))
                .then(literal("encounter").executes(c->encounter(c.getSource(),false)))
                .then(literal("fleet").executes(c->encounter(c.getSource(),true)))
                .then(literal("status").executes(c->status(c.getSource())))
                .then(literal("perf").executes(c->performance(c.getSource())))
                .then(literal("clear").executes(c->clear(c.getSource())))
                .then(literal("clearall").executes(c->clearAll(c.getSource())))
                .then(literal("scuttle").executes(c->scuttle(c.getSource(),0))
                    .then(argument("seconds",IntegerArgumentType.integer(0,600))
                        .executes(c->scuttle(c.getSource(),
                            IntegerArgumentType.getInteger(c,"seconds")))))
                .then(literal("confidence").then(argument("value",
                    IntegerArgumentType.integer(1,100))
                    .executes(c->confidence(c.getSource(),
                        IntegerArgumentType.getInteger(c,"value")))))
                .then(literal("mode").then(argument("mode",StringArgumentType.word())
                    .suggests((c,b)->{
                        for(String m:new String[]{"auto","patrol","chase","flee",
                            "strafe","broadside","evade","parallel","ram"})b.suggest(m);
                        return b.buildFuture();
                    }).executes(c->mode(c.getSource(),
                        StringArgumentType.getString(c,"mode"))))));
        });
        PirateSettings.load();
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            PirateShips.onLoad(entity,world);
            PirateSurvivors.onLoad(entity,world);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            PirateShips.onUnload(entity,world);
            PirateSurvivors.onUnload(entity,world);
        });
        ServerTickEvents.END_WORLD_TICK.register(PirateShips::tick);
        ServerTickEvents.END_WORLD_TICK.register(PirateBossBars::tick);
        ServerTickEvents.END_WORLD_TICK.register(PirateEncounters::tick);
        ServerTickEvents.END_WORLD_TICK.register(PirateSurvivors::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{
            PirateShips.reset();
            PirateSurvivors.reset();
        });
    }

    private static int summon(ServerCommandSource source,BlockPos pos,
                               PirateShipRoster.Hull hull) {
        Ship ship=PirateShips.spawn(source.getWorld(),pos,source.getRotation().y,hull,
            PirateShipRoster.randomWood(new java.util.Random()),
            null,null,null);
        if(ship==null){
            source.sendError(Text.literal(
                "Could not summon "+hull.display+": check deep water, loaded chunks and ship caps."));
            return 0;
        }
        source.sendFeedback(()->Text.literal(
            "Spawned "+hull.display+" ["+ship.getUuidAsString()+"]"),true);
        return 1;
    }

    private static int summonNear(ServerCommandSource source,String name) {
        PirateShipRoster.Hull hull=PirateShipRoster.Hull.parse(name);
        if(hull==null || hull==PirateShipRoster.Hull.GALLEON){
            source.sendError(Text.literal(
                "Use galley, drakkar, dhow, caravel, cog, brigg. Galleons are fleet-only."));
            return 0;
        }
        ServerWorld world=source.getWorld();
        Vec3d p=source.getPosition();
        for(int step=0;step<24;step++){
            double angle=world.random.nextDouble()*Math.PI*2;
            int radius=35+world.random.nextInt(30);
            BlockPos pos=new BlockPos((int)(p.x+Math.cos(angle)*radius),
                world.getSeaLevel(),(int)(p.z+Math.sin(angle)*radius));
            if(!PirateShips.validWater(world,pos,hull))continue;
            return summon(source,pos,hull);
        }
        source.sendError(Text.literal("No fully loaded deep-water site for "+name));
        return 0;
    }

    private static Ship nearest(ServerCommandSource source) {
        Ship closest=null;
        double limit=96*96;
        Vec3d pos=source.getPosition();
        for(Ship ship:PirateShips.loaded(source.getWorld())){
            if(ship.isRemoved())continue;
            double d=ship.squaredDistanceTo(pos);
            if(d<limit){limit=d;closest=ship;}
        }
        return closest;
    }
    private static int clear(ServerCommandSource source){
        Ship ship=nearest(source);
        if(ship==null){source.sendError(Text.literal("No pirate within 96 blocks."));return 0;}
        PirateShips.clear(source.getWorld(),ship,true);
        source.sendFeedback(()->Text.literal("Removed test pirate and its crew."),true);
        return 1;
    }
    private static int clearAll(ServerCommandSource source){
        int count=0;
        for(Ship ship:PirateShips.loaded(source.getWorld())){
            PirateShips.clear(source.getWorld(),ship,true);count++;
        }
        final int removed=count;
        source.sendFeedback(()->Text.literal("Cleared "+removed+" pirate vessels."),true);
        return count;
    }
    private static int status(ServerCommandSource source){
        Ship ship=nearest(source);
        if(ship==null){source.sendError(Text.literal("No pirate within 96 blocks."));return 0;}
        String status=PirateShips.status(ship);
        source.sendFeedback(()->Text.literal(status),false);
        return 1;
    }
    private static int performance(ServerCommandSource source){
        source.sendFeedback(()->Text.literal(PirateShips.performance(source.getWorld())),false);
        return 1;
    }
    private static int mode(ServerCommandSource source,String mode){
        if(!java.util.Set.of("auto","patrol","chase","flee","strafe",
            "broadside","evade","parallel","ram").contains(mode)){
            source.sendError(Text.literal("Unknown tactical mode "+mode));return 0;
        }
        Ship ship=nearest(source);
        if(ship==null){source.sendError(Text.literal("No pirate within 96 blocks."));return 0;}
        for(String tag:java.util.List.copyOf(ship.getCommandTags()))
            if(tag.startsWith("ia_mode_"))ship.removeScoreboardTag(tag);
        ship.addCommandTag("ia_mode_"+mode);
        source.sendFeedback(()->Text.literal("Pirate mode: "+mode),false);
        return 1;
    }
    private static int confidence(ServerCommandSource source,int level){
        Ship ship=nearest(source);
        if(ship==null){source.sendError(Text.literal("No pirate within 96 blocks."));return 0;}
        for(String tag:java.util.List.copyOf(ship.getCommandTags()))
            if(tag.startsWith("ia_conf_"))ship.removeScoreboardTag(tag);
        ship.addCommandTag("ia_conf_"+level);
        source.sendFeedback(()->Text.literal("Captain confidence="+level),true);return 1;
    }
    private static int scuttle(ServerCommandSource source,int seconds){
        Ship ship=nearest(source);
        if(ship==null){source.sendError(Text.literal("No pirate within 96 blocks."));return 0;}
        if(seconds==0)PirateShips.startSinking(source.getWorld(),ship,true);
        else ship.addCommandTag("ia_admin_scuttle_at_"+
            (source.getWorld().getTime()+20L*seconds));
        source.sendFeedback(()->Text.literal("Pirate scuttle ordered."),true);return 1;
    }
    private static int encounter(ServerCommandSource source,boolean fleet)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result=fleet?
            PirateEncounters.forceFleet(source.getWorld(),source.getPlayerOrThrow()):
            PirateEncounters.force(source.getWorld(),source.getPlayerOrThrow());
        if(result.startsWith("Spawned")){
            source.sendFeedback(()->Text.literal(result),true);return 1;
        }
        source.sendError(Text.literal(result));return 0;
    }
}
