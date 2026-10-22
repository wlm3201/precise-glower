package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
    private static final String SUBMIT =
            "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
    private static final String GET_RENDER_TYPE =
            "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType("
                    + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;";

    @WrapOperation(
            method = SUBMIT,
            at = @At(value = "INVOKE", target = GET_RENDER_TYPE),
            require = 0)
    private RenderType preciseglower$translucentBody(
            LivingEntityRenderer renderer,
            S state,
            boolean isBodyVisible,
            boolean forceTransparent,
            boolean appearGlowing,
            Operation<RenderType> original) {
        if (HiddenAlpha.current() >= 1F) {
            return original.call(renderer, state, isBodyVisible, forceTransparent, appearGlowing);
        }
        RenderType translucent = original.call(renderer, state, isBodyVisible, true, appearGlowing);
        return translucent != null
                ? translucent
                : original.call(renderer, state, isBodyVisible, forceTransparent, appearGlowing);
    }
}
