package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 竖滚动条的公共几何 / 绘制：规则列表、候选列表、补全弹层共用一套。
 *
 * <p>轨道只画 3px，命中区放宽到 {@link #HOTSPOT_W}，贴着控件右边缘。</p>
 */
public final class Scrollbar {
	/** 命中区宽度（比轨道宽，好按） */
	public static final int HOTSPOT_W = 6;
	private static final int BAR_W = 3;
	private static final int MIN_THUMB = 14;
	/** 轨道画在右边缘往左 5px 处 */
	private static final int INSET = 5;

	private Scrollbar() {
	}

	/** 滑块长度：视口固定时，内容越多滑块越短 */
	public static int thumbHeight(int viewH, int maxScroll) {
		if (viewH <= 0) return MIN_THUMB;
		return Math.max(MIN_THUMB, viewH * viewH / (viewH + Math.max(0, maxScroll)));
	}

	/** 鼠标是否落在滚动条的命中区（{@code right} 为控件右边界） */
	public static boolean hit(int right, int top, int height, double mx, double my) {
		return mx >= right - HOTSPOT_W && mx < right && my >= top && my < top + height;
	}

	/** 鼠标 y 换算成 scroll */
	public static int scrollAt(int top, int height, int maxScroll, double mouseY) {
		int thumbH = thumbHeight(height, maxScroll);
		int track = height - thumbH;
		if (maxScroll <= 0 || track <= 0) return 0;
		float t = Mth.clamp((float) (mouseY - top - thumbH / 2.0) / track, 0.0F, 1.0F);
		return Mth.clamp((int) (t * maxScroll), 0, maxScroll);
	}

	/** 画轨道 + 滑块；没有可滚动内容时什么都不画 */
	public static void draw(
		GuiGraphicsExtractor graphics, int right, int top, int height, int scroll, int maxScroll, boolean dragging
	) {
		if (maxScroll <= 0) return;
		int thumbH = thumbHeight(height, maxScroll);
		int thumbY = top + (height - thumbH) * scroll / maxScroll;
		int barX = right - INSET;
		graphics.fill(barX, top, barX + BAR_W, top + height, UiTheme.LINE);
		graphics.fill(barX, thumbY, barX + BAR_W, thumbY + thumbH, dragging ? UiTheme.ACCENT : UiTheme.WHITE);
	}
}
