package com.preciseglower.client.render;

import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * 移动方块（落沙 / 砂砾 / 铁砧）淡出的状态传递。
 *
 * <p>移动方块的 quad 是绘制阶段现从方块状态生成的，提交时拿不到颜色；而隐藏窗口
 * （{@code HiddenAlpha}）只在提交期有效，绘制期永远是 1。所以这里在提交阶段把
 * {@code MovingBlockRenderState} 实例登记下来，绘制阶段
 * {@code MovingBlockFeatureRenderer#buildGroup} 按 submit 取回 alpha、开窗口画。</p>
 *
 * <p>键是实例本身（未覆写 equals，即身份比较）；弱键，画完即回收。</p>
 */
public final class BlockFade {
	private static final Map<Object, Float> OWNED = new WeakHashMap<>();
	private static final ThreadLocal<Float> DRAW = ThreadLocal.withInitial(() -> 1F);

	private BlockFade() {
	}

	public static void markMoving(@Nullable Object movingBlockRenderState, float alpha) {
		if (movingBlockRenderState != null && alpha < 1F) OWNED.put(movingBlockRenderState, alpha);
	}

	public static float movingAlphaOf(@Nullable Object movingBlockRenderState) {
		Float alpha = movingBlockRenderState == null ? null : OWNED.get(movingBlockRenderState);
		return alpha == null ? 1F : alpha;
	}

	/** 绘制期窗口：只在「这一组确实是我们的 submit」时打开 */
	public static void begin(float alpha) {
		DRAW.set(alpha);
	}

	public static void end() {
		DRAW.set(1F);
	}

	public static float current() {
		return DRAW.get();
	}
}
