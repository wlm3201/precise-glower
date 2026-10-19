package com.preciseglower.client.gui;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.GlowerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 界面外框绘制（标题栏 / Tab / 栏头）。面板一律半透明，能看到后面的世界。 */
public final class Graphics {
	private Graphics() {
	}

	/** 鼠标是否落在矩形内（弹窗内的控件没人维护 isHovered，都靠这个自己算） */
	public static boolean hovered(int x, int y, int width, int height, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/**
	 * 填充一层「面板色」：与全屏遮罩同色时跳过，避免控件比周围面板深一层。
	 * 按钮常态 / 图标按钮常态都靠它保持一致。
	 */
	public static void fillSurface(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		if (color == UiTheme.MASK) return;
		graphics.fill(x0, y0, x1, y1, color);
	}

	/** 自绘叉号（两条对角线），不依赖字体 —— unicode 字体下「×」形状会很怪 */
	public static void drawX(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		for (int i = 0; i < size; i++) {
			graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color);
			graphics.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, color);
		}
	}

	/** 自绘三个点（用于「编辑」按钮） */
	public static void drawDots(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		graphics.fill(cx - 5, cy, cx - 4, cy + 1, color);
		graphics.fill(cx - 1, cy, cx, cy + 1, color);
		graphics.fill(cx + 3, cy, cx + 4, cy + 1, color);
	}

	/**
	 * 自绘箭头头（只有两条斜边、没有箭柄），用于「上移 / 下移」按钮。
	 *
	 * @param up true = ∧，false = ∨
	 */
	public static void drawChevron(GuiGraphicsExtractor graphics, int cx, int cy, int color, boolean up) {
		int half = 3;
		int top = cy - 2;
		for (int i = 0; i <= half; i++) {
			int leftX = cx - half + i;
			int leftY = up ? top + half - i : top + i;
			graphics.fill(leftX, leftY, leftX + 1, leftY + 1, color);
			if (i == 0) continue; // 顶点只画一次，否则中间会留下两个像素的「箭柄」
			int rightX = cx + i;
			int rightY = up ? top + i : top + half - i;
			graphics.fill(rightX, rightY, rightX + 1, rightY + 1, color);
		}
	}

	public static void tabs(
		GuiGraphicsExtractor graphics, Font font, int width, GlowerScreen.Tab tab, int mouseX, int mouseY
	) {
		int y = 0; // tab 栏顶
		graphics.fill(0, y + UiTheme.TAB_H, width, y + UiTheme.TAB_H + 1, UiTheme.LINE);

		// 关闭按钮：放在 tab 栏右侧，与 tab 栏同高
		// 与「删除条目」区分开：这里悬停只描红边框，不填充红；常态不叠底
		int cx = width - UiTheme.TAB_H;
		boolean closeHover = hovered(cx, 0, UiTheme.TAB_H, UiTheme.TAB_H, mouseX, mouseY);
		graphics.outline(cx, y, UiTheme.TAB_H, UiTheme.TAB_H, closeHover ? UiTheme.DANGER_SOLID : UiTheme.LINE);
		drawX(graphics, cx + 5, y + 5, 8, closeHover ? UiTheme.DANGER_SOLID : UiTheme.WHITE);

		GlowerConfig config = PreciseGlowerClient.config();
		for (GlowerScreen.Tab t : GlowerScreen.Tab.values()) {
			int x = t.ordinal() * UiTheme.TAB_W;
			boolean on = t == tab;
			boolean tabHovered = hovered(x, y, UiTheme.TAB_W, UiTheme.TAB_H, mouseX, mouseY);
			if (on) {
				graphics.fill(x, y, x + UiTheme.TAB_W, y + UiTheme.TAB_H, UiTheme.ACCENT_SOFT);
			} else if (tabHovered) {
				graphics.fill(x, y, x + UiTheme.TAB_W, y + UiTheme.TAB_H, UiTheme.MASK);
			}
			// 从 tab 栏顶一直连到底部横线
			graphics.fill(x + UiTheme.TAB_W, y, x + UiTheme.TAB_W + 1, y + UiTheme.TAB_H, UiTheme.LINE);

			String label = Component.translatable(switch (t) {
				case BLOCK -> "precise-glower.tab.blocks";
				case ENTITY -> "precise-glower.tab.entities";
				case ITEM -> "precise-glower.tab.items";
				case HIDE_ENTITY -> "precise-glower.tab.hide_entities";
				case HIDE_ITEM -> "precise-glower.tab.hide_items";
			}).getString();
			int count = switch (t) {
				case BLOCK -> config == null ? 0 : config.blocks.size();
				case ENTITY -> config == null ? 0 : config.entities.size();
				case ITEM -> config == null ? 0 : config.items.size();
				case HIDE_ENTITY -> config == null ? 0 : config.hiddenEntities.size();
				case HIDE_ITEM -> config == null ? 0 : config.hiddenItems.size();
			};
			String text = label + " (" + count + ")";
			graphics.text(font, text, x + 8, y + 5, UiTheme.WHITE, false);
		}
	}

	public static void sideHeader(GuiGraphicsExtractor graphics, Font font, String title) {
		sideHeader(graphics, font, title, UiTheme.WHITE);
	}

	/** 带颜色的版本：左侧栏标题位置可以临时充当提示行（比如「未知实体: xxx」） */
	public static void sideHeader(GuiGraphicsExtractor graphics, Font font, String title, int color) {
		int y = UiTheme.TAB_H;
		// 竖分割线由 GlowerScreen 一直画到底，这里只留表头下的横线
		graphics.fill(0, y + UiTheme.LIST_HEADER_H, UiTheme.SIDE_W, y + UiTheme.LIST_HEADER_H + 1, UiTheme.LINE);
		int textY = y + (UiTheme.LIST_HEADER_H - 8) / 2;
		graphics.text(font, font.plainSubstrByWidth(title, UiTheme.SIDE_W - 10), 5, textY, color, false);
	}

	public static void mainHeader(GuiGraphicsExtractor graphics, Font font, int width, String title) {
		int y = UiTheme.TAB_H;
		int x = UiTheme.SIDE_W + 1;
		graphics.fill(x, y + UiTheme.LIST_HEADER_H, width, y + UiTheme.LIST_HEADER_H + 1, UiTheme.LINE);
		graphics.text(font, title, x + 5, y + (UiTheme.LIST_HEADER_H - 8) / 2, UiTheme.WHITE, false);
	}
}
