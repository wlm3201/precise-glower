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

/**
 * 26.1.2：gizmo 收集入口方法名为 {@code collectPerFrameGizmos}
 * （26.2 / 26.3 改名为 {@code collectPerFrameRenderThreadGizmos}）。
 * 注入点在 RETURN —— 此时 {@code Gizmos} 的 ThreadLocal collector 已注册，可以直接提交。
 *
 * <p>描边部分：26.1.2 没有 {@code hasAnyOutline()} 这一层，放行开关是
 * {@code LevelRenderState#haveGlowingEntities} 字段（后处理链和帧图挂载都看它）；
 * 绘制点选在原版 {@code OutlineBufferSource#endOutlineBatch()} 之后，
 * 也就是原版刚把实体描边写进 outline 缓冲的时候。</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Inject(method = "collectPerFrameGizmos", at = @At("RETURN"))
	private void preciseglower$collectGizmos(CallbackInfoReturnable<TemporaryCollection> cir) {
		PreciseGlowerClient.renderGizmos();
	}

	@Inject(
		method = "lambda$addMainPass$0",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V",
			shift = At.Shift.AFTER
		),
		require = 0
	)
	private void preciseglower$drawBlockOutline(CallbackInfo ci) {
		BlockOutlineRenderer.renderOutline(((LevelRenderer) (Object) this).entityOutlineTarget());
	}

	/**
	 * 即使原版这一帧没有发光实体，也要让描边缓冲参与合成。
	 *
	 * <p>要同时命中 {@code renderLevel} 与 {@code addMainPass}：{@code addMainPass} 里的
	 * 那次决定描边缓冲是否挂进帧图，{@code renderLevel} 里的那次才决定
	 * {@code entity_outline} 后处理链（负责把遮罩提取成轮廓）跑不跑 —— 少了后者，
	 * 遮罩会被原样贴到主缓冲上，变成一整块实心色块。</p>
	 */
	@ModifyExpressionValue(
		method = { "renderLevel", "addMainPass" },
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/level/LevelRenderState;haveGlowingEntities:Z",
			opcode = Opcodes.GETFIELD
		),
		require = 0
	)
	private boolean preciseglower$forceGlowingEntities(boolean original) {
		BlockOutlineRenderer.noteVanillaOutline(original);
		return original || BlockOutlineRenderer.hasMask();
	}
}
