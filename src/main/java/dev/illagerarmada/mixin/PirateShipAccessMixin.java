package dev.illagerarmada.mixin;

import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.Attributes;
import dev.illagerarmada.PirateSettings;
import dev.illagerarmada.IllagerArmada;
import dev.illagerarmada.PirateShips;
import dev.illagerarmada.PirateCrew;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only the designated alive captain of an Illager Armada ship may drive it.
 * Only the living captain or a formally promoted first mate can control
 * a pirate vessel. This is the native Small Ships helm permission gate.
 */
@Mixin(Ship.class)
public abstract class PirateShipAccessMixin {
    @Inject(method="getAttributes",at=@At("RETURN"),remap=false)
    private void illagerArmada$hostileHull(CallbackInfoReturnable<Attributes> cir){
        Ship ship=(Ship)(Object)this;
        // Player-owned ships are never modified.
        if(!ship.getCommandTags().contains(IllagerArmada.SHIP))return;
        Attributes attributes=cir.getReturnValue();
        if(attributes!=null)attributes.maxHealth *=
            PirateSettings.ACTIVE.hostileHullHealthMultiplier;
    }

    @Inject(method="canDrive",at=@At("HEAD"),cancellable=true,remap=false)
    private void illagerArmada$driver(Entity rider,CallbackInfoReturnable<Boolean> cir){
        Ship ship=(Ship)(Object)this;
        if(!ship.getCommandTags().contains(IllagerArmada.SHIP))return;
        boolean captain=rider!=null && rider.isAlive() &&
            rider.getCommandTags().contains(PirateCrew.CAPTAIN);
        cir.setReturnValue(captain && !ship.isSinking() && !ship.isSunken());
    }
}
