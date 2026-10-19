package com.preciseglower.client.render;

/**
 * 半透明渲染隐藏实体的当前不透明度。
 *
 * <p>渲染管线是「先 extract 出 {@code EntityRenderState}，再统一 submit」的两段式，
 * submit 阶段拿不到实体对象。所以做法是：</p>
 * <ol>
 *     <li>{@code EntityRenderer#extractRenderState} 把该实体的不透明度记到 render state 上
 *         （见 {@code ver.EntityRendererMixin}）</li>
 *     <li>{@code EntityRenderDispatcher#submit} 包一层，把它取出来放进线程局部变量
 *         （见 {@code EntityRenderDispatcherMixin}）</li>
 *     <li>{@code ver.LivingEntityRendererMixin} 在提交模型时改颜色 alpha，并强制走半透明
 *         渲染层</li>
 * </ol>
 */
public final class HiddenAlpha {
	private static final ThreadLocal<Float> CURRENT = ThreadLocal.withInitial(() -> 1F);

	private HiddenAlpha() {
	}

	/** 1 = 不处理；0 = 完全隐藏（调用方一般会直接跳过渲染） */
	public static float current() {
		return CURRENT.get();
	}

	/** 在 {@code EntityRenderDispatcher#submit} 前后成对调用 */
	public static void begin(float alpha) {
		CURRENT.set(alpha);
	}

	public static void end() {
		CURRENT.set(1F);
	}

	/** 把 alpha 应用到 ARGB 上（保持 RGB 不变） */
	public static int apply(int argb, float alpha) {
		if (alpha >= 1F) return argb;
		if (alpha <= 0F) return argb & 0x00FFFFFF;
		int a = Math.round(255 * alpha);
		return (argb & 0x00FFFFFF) | (a << 24);
	}


}
