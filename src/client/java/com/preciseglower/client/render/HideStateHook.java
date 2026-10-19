package com.preciseglower.client.render;

/**
 * {@code EntityRenderState} 上的隐藏不透明度（由 mixin 挂上去，见 {@code HideStateMixin}）。
 *
 * <p>刻意<strong>不</strong>放在 mixin 包里：Mixin 把 mixin 包下的类一律当作混入类，
 * 直接引用会抛 IllegalClassLoadError。</p>
 */
public interface HideStateHook {
	void preciseglower$setAlpha(float alpha);

	float preciseglower$getAlpha();
}
