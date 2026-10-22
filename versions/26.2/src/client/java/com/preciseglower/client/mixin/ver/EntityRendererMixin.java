package com.preciseglower.client.mixin.ver;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.render.HideStateHook;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void preciseglower$shouldRender(
            T entity,
            Frustum culler,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (PreciseGlowerClient.hideTracker().opacityFor(entity) <= 0F) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void preciseglower$extractAlpha(
            Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ((HideStateHook) state)
                .preciseglower$setAlpha(PreciseGlowerClient.hideTracker().opacityFor(entity));
    }
}
