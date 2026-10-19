package com.preciseglower.client.mixin.ver;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.render.HideStateHook;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.1.2 / 26.2：隐藏指定实体 / 掉落物的渲染（在 shouldRender 处短路）。
 * 这一版的签名是 {@code shouldRender(T, Frustum, D, D, D)}；26.3 末尾多了一个 float，
 * 所以按版本各写一份，不能用「两条 require=0 的注入」凑 —— Mixin 会先按名字找到方法再校验签名，
 * 签名不匹配时照样抛 InvalidInjectionException。
 *
 * <p>只有规则把不透明度设成 0 才真的不渲染，其余情况按半透明渲染。</p>
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void preciseglower$shouldRender(
		T entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir
	) {
		if (PreciseGlowerClient.hideTracker().opacityFor(entity) <= 0F) {
			cir.setReturnValue(false);
			cir.cancel();
		}
	}

	/** 把这条渲染状态对应的不透明度记下来，submit 阶段要用 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void preciseglower$extractAlpha(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		((HideStateHook) state).preciseglower$setAlpha(PreciseGlowerClient.hideTracker().opacityFor(entity));
	}
}
