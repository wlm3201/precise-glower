package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.preciseglower.client.render.AlphaVertexConsumer;
import com.preciseglower.client.render.BlockFade;
import com.preciseglower.client.render.FadedRenderTypes;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
    @WrapMethod(method = "getVertexBuilder")
    protected VertexConsumer preciseglower$wrapFaded(
            RenderType renderType, Operation<VertexConsumer> original) {
        float alpha = FadedRenderTypes.alphaOf(renderType);
        if (alpha >= 1F) {
            alpha = BlockFade.current();
            if (alpha < 1F) renderType = RenderTypes.translucentMovingBlock();
        }
        VertexConsumer buffer = original.call(renderType);
        return alpha < 1F ? new AlphaVertexConsumer(buffer, alpha) : buffer;
    }
}
