package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.render.BlockOutlineRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private RenderTarget entityOutlineTarget;

    @Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("RETURN"))
    private void preciseglower$collectGizmos(CallbackInfoReturnable<TemporaryCollection> cir) {
        PreciseGlowerClient.renderGizmos();
    }

    @Inject(
            method = "lambda$addMainPass$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;"
                                            + "executeOutline(Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V",
                            shift = At.Shift.AFTER))
    private void preciseglower$drawBlockOutline(CallbackInfo ci) {
        BlockOutlineRenderer.renderOutline(entityOutlineTarget);
    }

    @ModifyExpressionValue(
            method = {"submitFeatures", "lambda$addMainPass$0"},
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/state/level/LevelRenderState;shouldShowEntityOutlines:Z",
                            opcode = Opcodes.GETFIELD),
            require = 0)
    private boolean preciseglower$forceEntityOutlines(boolean original) {
        return original || BlockOutlineRenderer.hasMask();
    }

    @ModifyExpressionValue(
            method = {"render", "addMainPass"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;hasAnyOutline()Z"),
            require = 0)
    private boolean preciseglower$forceAnyOutline(boolean original) {
        BlockOutlineRenderer.noteVanillaOutline(original);
        return original || BlockOutlineRenderer.hasMask();
    }
}
