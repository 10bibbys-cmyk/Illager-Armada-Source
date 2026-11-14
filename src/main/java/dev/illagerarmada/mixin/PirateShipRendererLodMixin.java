package dev.illagerarmada.mixin;

import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.client.renderer.entity.ShipRenderer;
import dev.illagerarmada.PirateSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional visual-only long-distance cannon LOD.
 * Native Small Ships draws every installed cannon's high-detail model each
 * frame (10 per Galleon). At distance, keep the hull, sails and banners
 * visible but omit tiny gun models. Physics, shots, hitboxes, AI and crew
 * are completely unchanged. Applies to all Small Ships vessels, not just
 * Illagers; user can disable this or raise the distance in the config.
 */
@Mixin(value=ShipRenderer.class,remap=false)
public abstract class PirateShipRendererLodMixin {
    @Inject(method="renderCannon",at=@At("HEAD"),cancellable=true,
            remap=false,require=0)
    private void illagerArmada$distantCannons(Cannonable vessel, float yaw,
            float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
            int light, CallbackInfo info){
        PirateSettings settings=PirateSettings.ACTIVE;
        if(!settings.distantCannonLodEnabled || settings.cannonRenderLodDistance<=0)
            return;
        Entity camera=MinecraftClient.getInstance().getCameraEntity();
        if(camera==null)return;
        int distance=settings.cannonRenderLodDistance;
        if(camera.squaredDistanceTo(vessel.self())>(double)distance*distance)
            info.cancel();
    }
}
