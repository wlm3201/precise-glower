package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EyesLayer.class)
public class EyesLayerMixin {
    private static final String SUBMIT_MODEL =
            "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
                    + "Lnet/minecraft/client/model/Model;"
                    + "Ljava/lang/Object;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                    + "III"
                    + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = SUBMIT_MODEL))
    private void preciseglower$skipEyes(
            OrderedSubmitNodeCollector collector,
            Model<?> model,
            Object state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            Operation<Void> original) {
        if (HiddenAlpha.current() < 1F) return;
        original.call(
                collector,
                model,
                state,
                poseStack,
                renderType,
                lightCoords,
                overlayCoords,
                outlineColor,
                crumblingOverlay);
    }
}
