package com.preciseglower.client.mixin;

import com.preciseglower.client.PreciseGlowerClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 方块变化的底层汇聚点是 {@code Level#setBlock}，客户端所有路径都会走到这里：
 * <ul>
 *     <li>玩家放置 / 破坏的本地预测</li>
 *     <li>服务器单块更新 —— {@code ClientPacketListener#handleBlockUpdate}
 *         → {@code ClientLevel#setServerVerifiedBlockState} → {@code super.setBlock(...)}</li>
 *     <li>服务器批量更新 —— {@code handleChunkBlocksUpdate} 同样走上面那条</li>
 * </ul>
 * 关键是<b>不能只 hook {@code ClientLevel#setBlock}</b>：{@code setServerVerifiedBlockState} 里写的是
 * {@code super.setBlock(...)}（invokespecial，直接调父类方法，不走虚分派），
 * 子类上的注入完全不会被触发 —— 这正是「只有破坏 / 放置能更新，方块状态变化不更新」的原因。
 */
@Mixin(Level.class)
public class LevelMixin {
	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("RETURN")
	)
	private void preciseglower$onSetBlock(
		BlockPos pos, BlockState state, int updateFlags, int updateLimit, CallbackInfoReturnable<Boolean> cir
	) {
		Level self = (Level) (Object) this;
		if (!self.isClientSide()) return;
		if (!cir.getReturnValue()) return;
		PreciseGlowerClient.blockTracker().onBlockChanged(pos, state);
	}
}
