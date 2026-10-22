package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
    private static final String RENDER_LAYERS_DETAIL =
            "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/resources/ResourceKey;"
                    + "Lnet/minecraft/client/model/Model;"
                    + "Ljava/lang/Object;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "I"
                    + "Lnet/minecraft/resources/Identifier;"
                    + "II)V";
    private static final String ARMOR_CUTOUT =
            "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull("
                    + "Lnet/minecraft/resources/Identifier;)"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;";
    private static final String ARMOR_CUTOUT_GLINT =
            "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint("
                    + "Lnet/minecraft/resources/Identifier;)"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;";
    private static final String SUBMIT_MODEL =
            "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
                    + "Lnet/minecraft/client/model/Model;"
                    + "Ljava/lang/Object;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                    + "III"
                    + "Lnet/minecraft/client/renderer/texture/UvMapping;"
                    + "I)V";
    private static final String ARMOR_TRIM =
            "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim("
                    + "Lnet/minecraft/resources/Identifier;Z)"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;";

    @WrapOperation(method = RENDER_LAYERS_DETAIL, at = @At(value = "INVOKE", target = ARMOR_CUTOUT))
    private RenderType preciseglower$translucentArmor(
            Identifier texture, Operation<RenderType> original) {
        if (HiddenAlpha.current() >= 1F) return original.call(texture);
        return RenderTypes.entityTranslucent(texture);
    }

    @WrapOperation(
            method = RENDER_LAYERS_DETAIL,
            at = @At(value = "INVOKE", target = ARMOR_CUTOUT_GLINT))
    private RenderType preciseglower$translucentArmorGlint(
            Identifier texture, Operation<RenderType> original) {
        if (HiddenAlpha.current() >= 1F) return original.call(texture);
        return RenderTypes.entityTranslucent(texture);
    }

    @WrapOperation(method = RENDER_LAYERS_DETAIL, at = @At(value = "INVOKE", target = ARMOR_TRIM))
    private RenderType preciseglower$translucentTrim(
            Identifier texture, boolean decal, Operation<RenderType> original) {
        if (HiddenAlpha.current() >= 1F) return original.call(texture, decal);
        return RenderTypes.entityTranslucent(texture);
    }

    @WrapOperation(method = RENDER_LAYERS_DETAIL, at = @At(value = "INVOKE", target = SUBMIT_MODEL))
    private void preciseglower$skipTrimGlint(
            OrderedSubmitNodeCollector collector,
            Model<?> model,
            Object state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int color,
            UvMapping uvMapping,
            int outlineColor,
            Operation<Void> original) {
        if (HiddenAlpha.current() < 1F && renderType.equals(RenderTypes.trimmedArmorGlint()))
            return;
        original.call(
                collector,
                model,
                state,
                poseStack,
                renderType,
                lightCoords,
                overlayCoords,
                color,
                uvMapping,
                outlineColor);
    }
}
