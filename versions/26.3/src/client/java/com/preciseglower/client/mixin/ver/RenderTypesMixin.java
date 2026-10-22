package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderTypes.class)
public class RenderTypesMixin {
    private static final String IDENTIFIER =
            "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;";
    private static final String IDENTIFIER_BOOL =
            "(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;";

    @WrapMethod(method = "entitySolid" + IDENTIFIER)
    private static RenderType preciseglower$solid(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    @WrapMethod(method = "entitySolidZOffsetForward" + IDENTIFIER)
    private static RenderType preciseglower$solidZOffset(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    @WrapMethod(method = "entityCutout" + IDENTIFIER)
    private static RenderType preciseglower$cutout(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    @WrapMethod(method = "entityCutout" + IDENTIFIER_BOOL)
    private static RenderType preciseglower$cutoutAffectsOutline(
            Identifier texture, boolean affectsOutline, Operation<RenderType> original) {
        return HiddenAlpha.current() >= 1F
                ? original.call(texture, affectsOutline)
                : RenderTypes.entityTranslucent(texture, affectsOutline);
    }

    @WrapMethod(method = "entityCutoutCull" + IDENTIFIER)
    private static RenderType preciseglower$cutoutCull(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    @WrapMethod(method = "entityCutoutZOffset" + IDENTIFIER)
    private static RenderType preciseglower$cutoutZOffset(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    @WrapMethod(method = "entityCutoutZOffset" + IDENTIFIER_BOOL)
    private static RenderType preciseglower$cutoutZOffsetAffectsOutline(
            Identifier texture, boolean affectsOutline, Operation<RenderType> original) {
        return HiddenAlpha.current() >= 1F
                ? original.call(texture, affectsOutline)
                : RenderTypes.entityTranslucent(texture, affectsOutline);
    }

    @WrapMethod(method = "eyes" + IDENTIFIER)
    private static RenderType preciseglower$eyes(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    @WrapMethod(method = "breezeEyes" + IDENTIFIER)
    private static RenderType preciseglower$breezeEyes(
            Identifier texture, Operation<RenderType> original) {
        return translucent(texture, original);
    }

    private static RenderType translucent(Identifier texture, Operation<RenderType> original) {
        return HiddenAlpha.current() >= 1F
                ? original.call(texture)
                : RenderTypes.entityTranslucent(texture);
    }
}
