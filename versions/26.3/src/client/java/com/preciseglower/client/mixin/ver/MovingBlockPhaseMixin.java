package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
public class MovingBlockPhaseMixin {
    private static final String SUBMIT_MOVING_BLOCK =
            "submitMovingBlock(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/block/MovingBlockRenderState;I)V";
    private static final String HAS_MATERIAL_FLAG =
            "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z";

    @WrapOperation(
            method = SUBMIT_MOVING_BLOCK,
            at = @At(value = "INVOKE", target = HAS_MATERIAL_FLAG))
    private boolean preciseglower$forceTranslucentPhase(
            BlockStateModel model, int flag, Operation<Boolean> original) {
        boolean translucent = original.call(model, flag);
        if (translucent || HiddenAlpha.current() >= 1F) return translucent;
        return flag == BakedQuad.FLAG_TRANSLUCENT;
    }
}
