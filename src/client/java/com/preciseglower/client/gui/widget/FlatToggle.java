package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.UiTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatToggle extends AbstractWidget {
    private final Component label;
    private final Consumer<Boolean> onChanged;
    private final Minecraft mc = Minecraft.getInstance();
    private boolean value;

    public FlatToggle(
            int x, int y, int width, Component label, boolean value, Consumer<Boolean> onChanged) {
        super(x, y, width, 11, label);
        this.label = label;
        this.value = value;
        this.onChanged = onChanged;
    }

    public boolean value() {
        return value;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int end = getX() + 13 + mc.font.width(label) + 2;
        return mouseX >= getX() - 2
                && mouseX <= end
                && mouseY >= getY()
                && mouseY <= getY() + getHeight();
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = mc.font;
        int boxY = getY() + 1;
        boolean hovered = isMouseOver(mouseX, mouseY);
        if (hovered) {
            graphics.fill(
                    getX() - 2,
                    getY(),
                    getX() + 13 + font.width(label) + 2,
                    getY() + 11,
                    UiTheme.MASK);
        }
        graphics.fill(getX(), boxY, getX() + 9, boxY + 9, UiTheme.MASK);
        graphics.outline(getX(), boxY, 9, 9, hovered ? UiTheme.ACCENT : UiTheme.LINE);
        if (value) {
            graphics.fill(getX() + 2, boxY + 2, getX() + 7, boxY + 7, UiTheme.ACCENT);
        }
        graphics.text(font, label, getX() + 13, getY() + 2, UiTheme.WHITE, false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        value = !value;
        if (onChanged != null) onChanged.accept(value);
    }

    @Override
    protected void updateWidgetNarration(
            net.minecraft.client.gui.narration.NarrationElementOutput output) {}
}
