package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.preciseglower.client.render.AlphaVertexConsumer;
import com.preciseglower.client.render.BlockFade;
import com.preciseglower.client.render.FadedRenderTypes;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
    @ModifyReturnValue(method = "getVertexBuilder", at = @At("RETURN"))
    protected VertexConsumer preciseglower$wrapFaded(
            VertexConsumer original, RenderType renderType) {
        float alpha = FadedRenderTypes.alphaOf(renderType);
        if (alpha >= 1F) {
            alpha = BlockFade.current();
        }
        return alpha < 1F ? new AlphaVertexConsumer(original, alpha) : original;
    }
}
