package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.Graphics;
import com.preciseglower.client.gui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** 深色扁平按钮（normal / primary / danger 三种样式）。 */
public class FlatButton extends AbstractWidget {
	public enum Style { NORMAL, PRIMARY, DANGER }

	public interface Press {
		void onPress(FlatButton button);
	}

	private static final long CONFIRM_TIMEOUT_MS = 3000;

	private final Press onPress;
	private final Style style;
	private final Minecraft mc = Minecraft.getInstance();
	/** 悬停时边框颜色（0 = 按样式默认） */
	private int hoverBorder;
	/** 悬停时文字颜色（0 = 沿用边框色或默认白） */
	private int hoverText;
	/** 危险操作需要二次确认：点一次先染色，再点一次才真正触发 */
	private boolean confirm;
	/** 已经点了第一次、等待二次确认 */
	private boolean armed;
	private long armedAt;

	public FlatButton(int x, int y, int width, int height, Component message, Style style, Press onPress) {
		super(x, y, width, height, message);
		this.style = style;
		this.onPress = onPress;
	}

	/** 设置悬停时的边框颜色（链式） */
	public FlatButton hoverBorder(int color) {
		this.hoverBorder = color;
		return this;
	}

	/** 设置悬停时的文字颜色（链式） */
	public FlatButton hoverText(int color) {
		this.hoverText = color;
		return this;
	}

	/** 开启二次确认（链式）：首次点击只染色，{@link #CONFIRM_TIMEOUT_MS} 内再点一次才触发 */
	public FlatButton requireConfirm() {
		this.confirm = true;
		return this;
	}

	/** 取消待确认状态（外部改动列表后调用，避免残留红底） */
	public void disarm() {
		armed = false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = mc.font;
		// 二次确认超时自动复位
		if (armed && System.currentTimeMillis() - armedAt > CONFIRM_TIMEOUT_MS) armed = false;
		// 弹窗里的按钮不是 screen 的 child，isHovered 不会更新，自己按鼠标位置算
		boolean hovered = Graphics.hovered(getX(), getY(), width, height, mouseX, mouseY);

		int bg = UiTheme.MASK;
		int border = UiTheme.LINE;
		int text = UiTheme.WHITE;

		if (!active) {
			bg = UiTheme.MASK;
			border = UiTheme.LINE;
			text = UiTheme.LINE;
		} else if (hovered) {
			// 危险按钮悬停只是半透明红，真正点了才是实心红
			bg = style == Style.DANGER ? UiTheme.DANGER : UiTheme.MASK;
			border = style == Style.DANGER ? UiTheme.DANGER_SOLID : (style == Style.PRIMARY ? UiTheme.ACCENT : UiTheme.WHITE);
			if (hoverBorder != 0) border = hoverBorder;
			if (hoverText != 0) text = hoverText;
			else if (hoverBorder != 0) text = hoverBorder;
		} else if (style == Style.PRIMARY) {
			border = UiTheme.ACCENT_SOFT;
			text = UiTheme.ACCENT;
		} else if (style == Style.DANGER) {
			border = UiTheme.DANGER_SOLID;
			text = UiTheme.DANGER_SOLID;
		}

		// 等待二次确认：整块填充实心红，跟悬停的半透明红区分开
		if (armed) {
			bg = UiTheme.DANGER_SOLID;
			border = UiTheme.DANGER_SOLID;
			text = UiTheme.WHITE;
		}

		Graphics.fillSurface(graphics, getX(), getY(), getX() + width, getY() + height, bg);
		graphics.outline(getX(), getY(), width, height, border);

		Component label = getMessage();
		int maxW = width - 6;
		if (font.width(label) > maxW) {
			graphics.text(font, font.plainSubstrByWidth(label.getString(), maxW), getX() + 3, getY() + (height - 8) / 2 + 1, text, false);
		} else {
			graphics.centeredText(font, label, getX() + width / 2, getY() + (height - 8) / 2 + 1, text);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!active) return;
		// 需要二次确认时，第一次点击只染色，不触发动作
		if (confirm && !armed) {
			armed = true;
			armedAt = System.currentTimeMillis();
			return;
		}
		armed = false;
		if (onPress != null) onPress.onPress(this);
	}

	@Override
	public void playDownSound(SoundManager handler) {
		if (active) playButtonClickSound(handler);
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
