package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.render.BlockOutlineRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "collectPerFrameGizmos", at = @At("RETURN"))
    private void preciseglower$collectGizmos(CallbackInfoReturnable<TemporaryCollection> cir) {
        PreciseGlowerClient.renderGizmos();
    }

    @Inject(
            method = "lambda$addMainPass$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V",
                            shift = At.Shift.AFTER),
            require = 0)
    private void preciseglower$drawBlockOutline(CallbackInfo ci) {
        BlockOutlineRenderer.renderOutline(((LevelRenderer) (Object) this).entityOutlineTarget());
    }

    @ModifyExpressionValue(
            method = {"renderLevel", "addMainPass"},
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/state/level/LevelRenderState;haveGlowingEntities:Z",
                            opcode = Opcodes.GETFIELD),
            require = 0)
    private boolean preciseglower$forceGlowingEntities(boolean original) {
        BlockOutlineRenderer.noteVanillaOutline(original);
        return original || BlockOutlineRenderer.hasMask();
    }
}
