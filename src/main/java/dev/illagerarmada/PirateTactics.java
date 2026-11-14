package dev.illagerarmada;

import com.talhanation.smallships.world.entity.ship.ContainerShip;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;

/**
 * Ship-first threat assessment. Only player-occupied vessels qualify.
 * "Cargo" is a coarse fullness estimate, never item value or player inventory.
 */
public final class PirateTactics {
    private PirateTactics() {}
    public record Target(Entity vessel, ServerPlayerEntity player, int size, int crew, double cargo,
                         double distanceSq) {}
    public record Assessment(double advantage, int confidence, int pirateSize, int targetSize,
                             int pirateCrew, int targetCrew, double pirateCargo, double targetCargo,
                             boolean engage) {}

    public static Target findTarget(ServerWorld world, Ship pirate, double radius) {
        Target nearest = null;
        double rangeSq = radius * radius;
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Never attack spectators or Creative players in public worlds.
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            Entity vessel = vesselOf(world, player);
            if (vessel == null || vessel == pirate || !vessel.isAlive()) continue;
            if (vessel.getCommandTags().contains(IllagerArmada.SHIP)) continue;
            double distanceSq = pirate.squaredDistanceTo(vessel);
            if (distanceSq > rangeSq) continue;
            if (nearest == null || distanceSq < nearest.distanceSq()) {
                nearest = new Target(vessel, player, sizeOf(vessel),
                    crewOf(vessel), cargoOf(vessel), distanceSq);
                rangeSq = distanceSq;
            }
        }
        return nearest;
    }

    public static Entity vesselOf(ServerWorld world, ServerPlayerEntity player) {
        Entity ridden = player.getVehicle();
        Entity last = null;
        while (ridden != null) {
            if (ridden instanceof Ship || ridden instanceof BoatEntity) last = ridden;
            ridden = ridden.getVehicle();
        }
        if (last != null) return last;

        // Walking on a Small Ships deck should count as sailing, but swimming beside
        // an empty ship must not. Only inspect already-loaded entities.
        // Local ship query: avoid scanning every loaded world entity for
        // every player on every pirate AI tick in multiplayer.
        for (Ship candidate : world.getEntitiesByClass(Ship.class,
                player.getBoundingBox().expand(24, 9, 24),
                ship -> ship.isAlive() && !ship.getCommandTags().contains(IllagerArmada.SHIP))) {
            if (candidate.getCommandTags().contains(IllagerArmada.SHIP)) continue;
            double dx = Math.abs(candidate.getX() - player.getX());
            double dz = Math.abs(candidate.getZ() - player.getZ());
            double dy = player.getY() - candidate.getY();
            if (dx <= Math.max(3.5, candidate.getBoundingBox().getXLength() * 0.5 + 1.0)
                && dz <= Math.max(3.5, candidate.getBoundingBox().getZLength() * 0.5 + 1.0)
                && dy >= 1.1 && dy <= 6.0 && !player.isTouchingWater()) {
                return candidate;
            }
        }
        return null;
    }

    public static Assessment assess(Ship pirate, Target target) {
        int confidence = confidence(pirate);
        int pirateSize = sizeOf(pirate);
        int pirateCrew = crewOf(pirate);
        double pirateCargo = cargoOf(pirate);
        double theirCargo = target.cargo();

        // Ships and crew represent combat strength. Cargo is an incentive and a
        // reason to preserve our vessel. Confidence only breaks close decisions.
        double ourPower = 1.25 * pirateSize + 0.85 * pirateCrew;
        double theirPower = 1.25 * target.size() + 0.85 * target.crew();
        double confidenceBias = (confidence - 50) / 25.0; // approximately -1..+2
        double plunderIncentive = 1.1 * theirCargo;
        double riskOfLoss = 0.9 * pirateCargo;
        double advantage = ourPower - theirPower + confidenceBias + plunderIncentive - riskOfLoss;

        return new Assessment(advantage, confidence, pirateSize, target.size(),
            pirateCrew, target.crew(), pirateCargo, theirCargo, advantage >= 0.0);
    }

    public static int confidence(Ship pirate) {
        for (String tag : pirate.getCommandTags()) {
            if (!tag.startsWith("ia_conf_")) continue;
            try { return MathHelper.clamp(Integer.parseInt(tag.substring("ia_conf_".length())), 1, 100); }
            catch (NumberFormatException ignored) {}
        }
        int value = 25 + Math.floorMod(pirate.getUuid().hashCode(), 71);
        pirate.addCommandTag("ia_conf_" + value);
        return value;
    }

    public static int sizeOf(Entity entity) {
        if (entity instanceof BoatEntity) return 1;
        if (!(entity instanceof Ship)) return 1;
        String type = entity.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("galleon")) return 7;
        if (type.contains("brigg") || type.contains("brig")) return 5;
        if (type.contains("cog") || type.contains("dhow")) return 4;
        if (type.contains("caravel") || type.contains("drakkar")) return 3;
        if (type.contains("galley")) return 2;
        return 3;
    }

    public static int crewOf(Entity vessel) {
        int crew = 0;
        for (Entity passenger : vessel.getPassengerList()) {
            if (passenger instanceof LivingEntity && passenger.isAlive()) crew++;
        }
        // Count players walking on a Small Ships deck as crewmates, not only
        // passengers. Avoid double-counting passengers and the helm occupant.
        if(vessel.getWorld() instanceof ServerWorld serverWorld) {
            for(ServerPlayerEntity player:serverWorld.getPlayers()) {
                if(!player.isAlive() || player.isSpectator())continue;
                if(vessel.getPassengerList().contains(player))continue;
                if(vesselOf(serverWorld,player)==vessel)crew++;
            }
        }
        return Math.max(1,crew);
    }

    public static double cargoOf(Entity entity) {
        if (entity instanceof ContainerShip cargo) {
            // Count occupied cargo slots, not contents. A filled hold signals
            // an approximate prize without magical knowledge of item values.
            int filled = 0, slots = cargo.size();
            if (slots <= 0) return 0.0;
            for (int i = 0; i < slots; i++) {
                net.minecraft.item.ItemStack stack=cargo.getStack(i);
                if (!stack.isEmpty() &&
                    !(stack.getItem() instanceof com.talhanation.smallships.world.item.CannonBallItem) &&
                    !stack.isOf(com.talhanation.smallships.world.item.ModItems.FINE_GRAIN_POWDER))
                    filled++;
            }
            return MathHelper.clamp((double)filled / slots, 0.0, 1.0);
        }
        // Vanilla chest boats expose the player-visible container.
        if (entity instanceof net.minecraft.entity.vehicle.ChestBoatEntity chest) {
            int filled = 0, slots = chest.size();
            for (int i = 0; i < slots; i++) if (!chest.getStack(i).isEmpty()) filled++;
            return slots > 0 ? (double)filled / slots : 0;
        }
        return 0.0;
    }
}
