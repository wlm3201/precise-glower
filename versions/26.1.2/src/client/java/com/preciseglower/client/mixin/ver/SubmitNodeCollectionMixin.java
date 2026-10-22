package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.preciseglower.client.render.BlockFade;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
    @WrapMethod(method = "submitModel")
    private <S> void preciseglower$applyAlpha(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            TextureAtlasSprite sprite,
            int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            Operation<Void> original) {
        original.call(
                model,
                state,
                poseStack,
                renderType,
                lightCoords,
                overlayCoords,
                HiddenAlpha.apply(tintedColor, HiddenAlpha.current()),
                sprite,
                outlineColor,
                crumblingOverlay);
    }

    @WrapMethod(method = "submitText")
    private void preciseglower$textAlpha(
            PoseStack poseStack,
            float x,
            float y,
            FormattedCharSequence string,
            boolean dropShadow,
            Font.DisplayMode displayMode,
            int lightCoords,
            int color,
            int backgroundColor,
            int outlineColor,
            Operation<Void> original) {
        float alpha = HiddenAlpha.current();
        original.call(
                poseStack,
                x,
                y,
                string,
                dropShadow,
                displayMode,
                lightCoords,
                HiddenAlpha.apply(color, alpha),
                HiddenAlpha.apply(backgroundColor, alpha),
                outlineColor);
    }

    @WrapMethod(method = "submitMovingBlock")
    private void preciseglower$markMovingBlock(
            PoseStack poseStack,
            net.minecraft.client.renderer.block.MovingBlockRenderState movingBlockRenderState,
            Operation<Void> original) {
        BlockFade.markMoving(movingBlockRenderState, HiddenAlpha.current());
        original.call(poseStack, movingBlockRenderState);
    }
}
