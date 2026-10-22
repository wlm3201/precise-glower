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

public class FlatButton extends AbstractWidget {
    public enum Style {
        NORMAL,
        PRIMARY,
        DANGER
    }

    public interface Press {
        void onPress(FlatButton button);
    }

    private static final long CONFIRM_TIMEOUT_MS = 3000;

    private final Press onPress;
    private final Style style;
    private final Minecraft mc = Minecraft.getInstance();

    private int hoverBorder;

    private int hoverText;

    private boolean confirm;

    private boolean armed;

    private long armedAt;

    public FlatButton(
            int x, int y, int width, int height, Component message, Style style, Press onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    public FlatButton hoverBorder(int color) {
        this.hoverBorder = color;
        return this;
    }

    public FlatButton hoverText(int color) {
        this.hoverText = color;
        return this;
    }

    public FlatButton requireConfirm() {
        this.confirm = true;
        return this;
    }

    public void disarm() {
        armed = false;
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = mc.font;
        if (armed && System.currentTimeMillis() - armedAt > CONFIRM_TIMEOUT_MS) armed = false;
        boolean hovered = Graphics.hovered(getX(), getY(), width, height, mouseX, mouseY);

        int bg = UiTheme.MASK;
        int border = UiTheme.LINE;
        int text = UiTheme.WHITE;

        if (!active) {
            bg = UiTheme.MASK;
            border = UiTheme.LINE;
            text = UiTheme.LINE;
        } else if (hovered) {
            bg = style == Style.DANGER ? UiTheme.DANGER : UiTheme.MASK;
            border =
                    style == Style.DANGER
                            ? UiTheme.DANGER_SOLID
                            : (style == Style.PRIMARY ? UiTheme.ACCENT : UiTheme.WHITE);
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
            graphics.text(
                    font,
                    font.plainSubstrByWidth(label.getString(), maxW),
                    getX() + 3,
                    getY() + (height - 8) / 2 + 1,
                    text,
                    false);
        } else {
            graphics.centeredText(
                    font, label, getX() + width / 2, getY() + (height - 8) / 2 + 1, text);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!active) return;
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
    protected void updateWidgetNarration(
            net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
