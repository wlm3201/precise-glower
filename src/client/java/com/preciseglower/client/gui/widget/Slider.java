package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.Graphics;
import com.preciseglower.client.gui.UiTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 水平滑块：左侧标签（可指定颜色），右侧数值。 */
public class Slider extends AbstractWidget {
	private final Component label;
	private final Consumer<Float> onChanged;
	private final Minecraft mc = Minecraft.getInstance();
	private final float min;
	private final float max;
	private final int valueWidth;
	private final int labelWidth;
	private final int labelColor;
	/** 轨道 / 滑块的强调色（0 = 用主题 ACCENT） */
	private final int accentColor;
	private float value;
	private boolean dragging;
	private final String format;

	public Slider(int x, int y, int width, Component label, float min, float max, float value, String format, Consumer<Float> onChanged) {
		this(x, y, width, label, min, max, value, format, onChanged, 74, UiTheme.WHITE, 0);
	}

	public Slider(
		int x, int y, int width, Component label, float min, float max, float value, String format,
		Consumer<Float> onChanged, int labelWidth, int labelColor, int accentColor
	) {
		super(x, y, width, 12, label);
		this.label = label;
		this.min = min;
		this.max = max;
		this.value = value;
		this.format = format;
		this.onChanged = onChanged;
		this.labelWidth = labelWidth;
		this.labelColor = labelColor;
		this.accentColor = accentColor;
		this.valueWidth = 30;
	}

	private int accent() {
		return accentColor == 0 ? UiTheme.ACCENT : accentColor;
	}

	public float value() {
		return value;
	}

	public void setValue(float value) {
		this.value = Mth.clamp(value, min, max);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = mc.font;
		int trackX = getX() + labelWidth;
		int trackW = width - labelWidth - valueWidth - 4;
		int trackY = getY() + 3;

		// 弹窗里的控件不是 screen 的 child，没人帮我们维护 isHovered，这里自己算
		boolean hovered = dragging || Graphics.hovered(getX(), getY(), width, height, mouseX, mouseY);

		graphics.text(font, label, getX(), getY() + 2, labelColor, false);
		int border = hovered ? UiTheme.WHITE : UiTheme.LINE;

		float t = (value - min) / Math.max(0.0001F, max - min);
		int knobX = trackX + (int) ((trackW - 4) * t);
		// 先画填充（含强调色进度条），最后画边框 —— 否则进度条会把左边框盖掉
		graphics.fill(trackX, trackY, trackX + trackW, trackY + 6, UiTheme.MASK);
		graphics.fill(trackX + 1, trackY + 1, knobX + 2, trackY + 5, accent());
		// 滑块始终是实心的强调色，悬停只改边框颜色
		graphics.fill(knobX, trackY - 1, knobX + 4, trackY + 7, accent());
		graphics.outline(trackX, trackY, trackW, 6, border);

		String text = format.formatted(value);
		// 数值与标签同色：R/G/B 三条的标签是通道色，数字跟着染成红 / 绿 / 蓝；
		// 其余滑块（线宽 / 范围 / 不透明度）标签是白色，数字也保持白色
		graphics.text(font, text, getX() + width - valueWidth + 6, getY() + 2, labelColor, false);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		dragging = true;
		updateFromMouse(event.x());
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		dragging = false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging) updateFromMouse(event.x());
		return dragging;
	}

	private void updateFromMouse(double mouseX) {
		int trackX = getX() + labelWidth;
		int trackW = width - labelWidth - valueWidth - 4;
		float t = Mth.clamp((float) (mouseX - trackX) / Math.max(1, trackW - 4), 0.0F, 1.0F);
		value = min + (max - min) * t;
		if (onChanged != null) onChanged.accept(value);
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
	}
}
