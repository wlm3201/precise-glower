package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderLayer.class)
public class RenderLayerMixin {
    private static final String RENDER_COLORED_CUTOUT_MODEL =
            "renderColoredCutoutModel(Lnet/minecraft/client/model/Model;"
                    + "Lnet/minecraft/resources/Identifier;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "I"
                    + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
                    + "II)V";
    private static final String ENTITY_CUTOUT =
            "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityCutout("
                    + "Lnet/minecraft/resources/Identifier;)"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;";

    @WrapOperation(
            method = RENDER_COLORED_CUTOUT_MODEL,
            at = @At(value = "INVOKE", target = ENTITY_CUTOUT))
    private static RenderType preciseglower$translucentOverlay(
            Identifier texture, Operation<RenderType> original) {
        if (HiddenAlpha.current() >= 1F) return original.call(texture);
        return RenderTypes.entityTranslucent(texture);
    }
}
