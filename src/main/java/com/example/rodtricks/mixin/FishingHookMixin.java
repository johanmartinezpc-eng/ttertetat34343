package com.example.rodtricks.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    /** Al recoger la cana, vanilla jala a la entidad enganchada; con las entidades estaticas no queremos eso. */
    @Inject(method = "pullEntity", at = @At("HEAD"), cancellable = true)
    private void rodtricks$noPullFrozen(Entity entity, CallbackInfo ci) {
        if (com.example.rodtricks.RodTricks.isFrozenType(entity)) {
            ci.cancel();
        }
    }
}
