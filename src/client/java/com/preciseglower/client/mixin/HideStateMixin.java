package com.preciseglower.client.mixin;

import com.preciseglower.client.render.HiddenAlpha;
import com.preciseglower.client.render.HideStateHook;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 {@code EntityRenderState} 挂一个「这条渲染状态对应的不透明度」。
 * 值在 {@code EntityRenderer#extractRenderState} 时写入，在 submit 时读出。
 */
@Mixin(EntityRenderState.class)
public class HideStateMixin implements HideStateHook {
	@Unique
	private float preciseglower$alpha = 1F;

	@Override
	public void preciseglower$setAlpha(float alpha) {
		this.preciseglower$alpha = alpha;
	}

	@Override
	public float preciseglower$getAlpha() {
		// 保险：极端情况下读到 0 就当没设置
		return preciseglower$alpha > 0F ? preciseglower$alpha : HiddenAlpha.current();
	}
}
