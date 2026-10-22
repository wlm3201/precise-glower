package com.preciseglower.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.preciseglower.client.render.FadedRenderTypes;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockModelRenderState.class)
public abstract class BlockModelRenderStateMixin {
    private static final String SUBMIT_MODEL =
            "submitModel(Lnet/minecraft/client/renderer/rendertype/RenderType;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    @WrapMethod(method = SUBMIT_MODEL)
    private void preciseglower$fadeBlockModel(
            RenderType renderType,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int externalLightCoords,
            int overlayCoords,
            int outlineColor,
            Operation<Void> original) {
        float alpha = HiddenAlpha.current();
        if (alpha >= 1F) {
            original.call(
                    renderType,
                    poseStack,
                    submitNodeCollector,
                    externalLightCoords,
                    overlayCoords,
                    outlineColor);
            return;
        }
        original.call(
                FadedRenderTypes.faded(alpha),
                poseStack,
                submitNodeCollector,
                externalLightCoords,
                overlayCoords,
                outlineColor);
    }
}
