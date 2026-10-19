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
 * 26.2：gizmo 收集入口为 {@code collectPerFrameRenderThreadGizmos}。
 * 注入点在 RETURN —— 此时 {@code Gizmos} 的 ThreadLocal collector 已注册，可以直接提交。
 *
 * <p>另外在原版描边阶段（{@code PreparedFrame#executeOutline()}）之后把方块遮罩追加进
 * 实体描边缓冲，实现「描边模式」。</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("RETURN"))
	private void preciseglower$collectGizmos(CallbackInfoReturnable<TemporaryCollection> cir) {
		PreciseGlowerClient.renderGizmos();
	}

	@Inject(
		method = "lambda$addMainPass$0",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeOutline()V",
			shift = At.Shift.AFTER
		)
	)
	private void preciseglower$drawBlockOutline(CallbackInfo ci) {
		BlockOutlineRenderer.renderOutline(((LevelRenderer) (Object) this).entityOutlineTarget());
	}

	/** 即使原版这一帧没有实体发光，也要让描边缓冲被合成到主缓冲 */
	@ModifyExpressionValue(
		method = { "doEntityOutline", "submitFeatures", "lambda$addMainPass$0" },
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/level/LevelRenderState;shouldShowEntityOutlines:Z",
			opcode = Opcodes.GETFIELD
		),
		require = 0
	)
	private boolean preciseglower$forceEntityOutlines(boolean original) {
		return original || BlockOutlineRenderer.hasMask();
	}

	/**
	 * 即使原版这一帧没有实体发光，也要让描边缓冲参与合成。
	 *
	 * <p>注意要同时命中 {@code render} 与 {@code addMainPass}：{@code addMainPass} 里的
	 * 那次决定描边缓冲是否挂进帧图，{@code render} 里的那次才决定
	 * {@code entity_outline} 后处理链（负责把遮罩提取成轮廓）跑不跑 —— 少了后者，
	 * 遮罩会被原样贴到主缓冲上，变成一整块实心色块。</p>
	 */
	@ModifyExpressionValue(
		method = { "render", "addMainPass" },
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;hasAnyOutline()Z"
		),
		require = 0
	)
	private boolean preciseglower$forceAnyOutline(boolean original) {
		BlockOutlineRenderer.noteVanillaOutline(original);
		return original || BlockOutlineRenderer.hasMask();
	}
}
