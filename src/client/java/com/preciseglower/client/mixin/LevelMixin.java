package com.preciseglower.client.mixin;

import com.preciseglower.client.PreciseGlowerClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(
            method =
                    "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void preciseglower$onSetBlock(
            BlockPos pos,
            BlockState state,
            int updateFlags,
            int updateLimit,
            CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide()) return;
        if (!cir.getReturnValue()) return;
        PreciseGlowerClient.blockTracker().onBlockChanged(pos, state);
    }
}
