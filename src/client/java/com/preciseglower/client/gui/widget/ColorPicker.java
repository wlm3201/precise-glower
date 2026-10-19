package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.util.Colors;
import com.mojang.logging.LogUtils;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/** 颜色选择：16 色预设 + 预览（含 HEX）+ 可编辑的 HEX 输入框。 */
public class ColorPicker extends AbstractWidget {
	private static final Logger LOG = LogUtils.getLogger();
	private static final int CELL_W = 17;
	private static final int GAP = 2;
	/**
	 * 每排色块与 HEX 框同高：两排 14+2+14 = 30，恰好等于右列「预览 + 间距 + HEX」，
	 * 左右两列等高、横缝对齐（之前压成 6px 一排，扁得没法看）。
	 */
	private static final int CELL_H = UiTheme.CONTROL_H;
	private static final int PRESET_COLS = 8;
	private static final int PRESET_W = PRESET_COLS * (CELL_W + GAP);
	private static final int RIGHT_GAP = 6;
	/** 预览条与 HEX 输入框同高 */
	private static final int COL_H = UiTheme.CONTROL_H;
	private static final int COL_GAP = 2;

	/** 整体高度：右列 = 预览 + 间距 + HEX 输入框 */
	public static int preferredHeight() {
		return COL_H + COL_GAP + UiTheme.CONTROL_H;
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final Consumer<Integer> onChanged;
	private final EditBox hexBox;
	private int color;
	private boolean syncing;
	/** 上一帧 HEX 框里的文本，用于「输入即应用」的轮询 */
	private String lastHex;
	/** debug 探针：避免日志刷屏 */
	private String lastLoggedHex = "\u0000";

	public ColorPicker(int x, int y, int width, int color, Consumer<Integer> onChanged) {
		super(x, y, width, preferredHeight(), Component.empty());
		this.color = color;
		this.onChanged = onChanged;
		int rx = x + PRESET_W + RIGHT_GAP;
		int rw = width - PRESET_W - RIGHT_GAP;
		this.hexBox = new EditBox(mc.font, rx, y + COL_H + COL_GAP, rw, UiTheme.CONTROL_H, Component.literal("hex"));
		this.hexBox.setMaxLength(8);
		this.hexBox.setValue(Colors.toHex(color));
		this.lastHex = this.hexBox.getValue();
		this.hexBox.setResponder(s -> {
		});
	}

	public int color() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
		syncing = true;
		if (hexBox != null) {
			hexBox.setValue(Colors.toHex(color));
			lastHex = hexBox.getValue();
		}
		syncing = false;
	}

	public EditBox hexBox() {
		return hexBox;
	}

	/** 从 HEX 输入框同步颜色（保存前的保险路径） */
	public void syncFromHexBox() {
		applyHex();
	}

	/**
	 * 渲染期轮询 HEX 框：文本变了就尝试应用（6 位 RRGGBB 或 8 位 RRGGBBAA）。
	 * 不依赖 responder / 焦点，输入合法的那一刻立即生效。
	 */
	private void applyHex() {
		if (syncing) return;
		String text = hexBox.getValue();
		OptionalInt parsed = Colors.fromHex(text);
		boolean applied = false;
		if (parsed.isPresent() && parsed.getAsInt() != color) {
			color = parsed.getAsInt();
			notifyChanged();
			applied = true;
		}
		// debug 探针：排查「输入不生效」时看这里（log 搜 ag-hex）
		if (!text.equals(lastLoggedHex)) {
			lastLoggedHex = text;
			LOG.info("[ag-hex] text='{}' parsed={} colorNow={} applied={}",
				text, parsed.isPresent() ? String.format("%08X", parsed.getAsInt()) : "invalid",
				String.format("%08X", color), applied);
		}
	}

	public int bottom() {
		return getY() + getHeight();
	}

	private void notifyChanged() {
		if (onChanged != null) onChanged.accept(color);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// 输入即应用：文本与上一帧不同就尝试解析
		String text = hexBox.getValue();
		if (!text.equals(lastHex)) {
			lastHex = text;
			applyHex();
		}

		for (int i = 0; i < Colors.PRESET.length; i++) {
			int col = i % PRESET_COLS;
			int row = i / PRESET_COLS;
			int cx = getX() + col * (CELL_W + GAP);
			int cy = getY() + row * (CELL_H + GAP);
			int preset = Colors.PRESET[i];
			graphics.fill(cx, cy, cx + CELL_W, cy + CELL_H, preset);
			boolean hovered = mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H;
			graphics.outline(cx, cy, CELL_W, CELL_H, equalsColor(preset) || hovered ? UiTheme.WHITE : UiTheme.LINE);
		}

		// 右列：预览在上（棋盘格底，调 alpha 时看得清）、HEX 输入在下
		int rx = getX() + PRESET_W + RIGHT_GAP;
		int rw = width - PRESET_W - RIGHT_GAP;
		for (int gx = 0; gx < rw; gx += 6) {
			for (int gy = 0; gy < COL_H; gy += 6) {
				int cw = Math.min(6, rw - gx);
				int ch = Math.min(6, COL_H - gy);
				boolean light = ((gx / 6) + (gy / 6)) % 2 == 0;
				graphics.fill(rx + gx, getY() + gy, rx + gx + cw, getY() + gy + ch,
					light ? UiTheme.WHITE : UiTheme.BLACK);
			}
		}
		graphics.fill(rx, getY(), rx + rw, getY() + COL_H, color);
		graphics.outline(rx, getY(), rw, COL_H, UiTheme.LINE);

		// 预览里不再叠 HEX 文本：下面的输入框里的就够了
		hexBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private boolean equalsColor(int preset) {
		return (preset & 0x00FFFFFF) == (color & 0x00FFFFFF);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		applyHex();
		if (hexBox.isMouseOver(event.x(), event.y())) {
			hexBox.setFocused(true);
			hexBox.onClick(event, doubleClick);
			return;
		}
		double mx = event.x();
		double my = event.y();
		for (int i = 0; i < Colors.PRESET.length; i++) {
			int col = i % PRESET_COLS;
			int row = i / PRESET_COLS;
			int cx = getX() + col * (CELL_W + GAP);
			int cy = getY() + row * (CELL_H + GAP);
			if (mx >= cx && mx < cx + CELL_W && my >= cy && my < cy + CELL_H) {
				color = Colors.withAlpha(Colors.PRESET[i], Colors.alpha(color));
				setColor(color);
				notifyChanged();
				return;
			}
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		// 让 HEX 框能鼠标拖选文字：否则 drag 事件到不了它（它不在弹窗 children 里）
		if (hexBox.isFocused()) return hexBox.mouseDragged(event, dx, dy);
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		if (hexBox.isFocused()) hexBox.onRelease(event);
		super.onRelease(event);
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
	}
}
