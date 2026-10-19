package com.preciseglower.client.config;

/**
 * 方块高亮的绘制方式。
 *
 * <p>界面上是两个独立勾选框（线框 / 描边），这里只负责保存组合结果：
 * 两个都勾是 {@link #BOTH}，都不勾就是 {@link #NONE}（该规则不绘制任何东西）。</p>
 */
public enum BlockMode {
	/** 自绘线框（Gizmo stroke），可以穿墙、线宽可调 */
	WIREFRAME,
	/** 描边：把方块写进原版实体描边缓冲，得到和实体发光一样的辉光轮廓 */
	OUTLINE,
	/** 线框 + 描边 */
	BOTH,
	/** 什么都不画 */
	NONE;

	public boolean wireframe() {
		return this == WIREFRAME || this == BOTH;
	}

	public boolean outline() {
		return this == OUTLINE || this == BOTH;
	}

	public static BlockMode of(boolean wireframe, boolean outline) {
		if (wireframe) return outline ? BOTH : WIREFRAME;
		return outline ? OUTLINE : NONE;
	}
}
