package com.preciseglower.client.gui.widget;

import com.preciseglower.client.config.Rule;
import com.preciseglower.client.gui.Graphics;
import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.util.Colors;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

public class RuleListWidget extends AbstractWidget {
    private static final int BTN_NONE = -1;

    private static final int BTN_EDIT = 0;
    private static final int BTN_DELETE = 1;
    private static final int BTN_TOGGLE = 2;
    private static final int BTN_SWATCH = 3;
    private static final int BTN_UP = 4;
    private static final int BTN_DOWN = 5;

    private static final int MIN_NAME_W = 60;

    public interface Callbacks {
        void onEdit(int index);

        void onDelete(int index);

        void onToggle(int index);

        void onMove(int index, int delta);
    }

    private static final int BTN = 20;
    private static final int SWATCH = 20;

    private static final int GAP = 2;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<? extends Rule> rules;
    private final Callbacks callbacks;

    private final boolean styled;

    private int scroll;
    private int maxScroll;
    private int hoveredRow = -1;
    private int hoveredButton = BTN_NONE;
    private boolean draggingScrollbar;

    private int pressedRow = -1;

    private int pressedButton = -1;

    public RuleListWidget(
            int x, int y, int width, int height, List<? extends Rule> rules, Callbacks callbacks) {
        this(x, y, width, height, rules, callbacks, true);
    }

    public RuleListWidget(
            int x,
            int y,
            int width,
            int height,
            List<? extends Rule> rules,
            Callbacks callbacks,
            boolean styled) {
        super(x, y, width, height, net.minecraft.network.chat.Component.empty());
        this.rules = rules;
        this.callbacks = callbacks;
        this.styled = styled;
    }

    private void clampScroll() {
        maxScroll = Math.max(0, rules.size() * UiTheme.ROW_H - (height - 4));
        scroll = Mth.clamp(scroll, 0, maxScroll);
    }

    private int contentY() {
        return getY() + 2;
    }

    private int contentH() {
        return height - 4;
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        clampScroll();
        hoveredRow = rowAt(mouseX, mouseY);
        hoveredButton = hoveredRow < 0 ? -1 : buttonAt(mouseX, mouseY);
        Font font = mc.font;
        int contentX = getX() + 1;
        int contentW = width - 2 - 4;

        graphics.enableScissor(contentX, contentY(), contentX + contentW, contentY() + contentH());
        for (int i = 0; i < rules.size(); i++) {
            int rowY = contentY() + i * UiTheme.ROW_H - scroll;
            if (rowY + UiTheme.ROW_H < contentY() || rowY > contentY() + contentH()) continue;
            renderRow(graphics, font, i, contentX, rowY, contentW);
        }
        graphics.disableScissor();

        Scrollbar.draw(
                graphics,
                getX() + width,
                contentY(),
                contentH(),
                scroll,
                maxScroll,
                draggingScrollbar);
    }

    private void renderRow(
            GuiGraphicsExtractor graphics, Font font, int index, int x, int y, int w) {
        Rule rule = rules.get(index);
        boolean hovered = hoveredRow == index && isHovered;

        if (hovered) graphics.fill(x, y, x + w, y + UiTheme.ROW_H, UiTheme.MASK);
        graphics.fill(x, y + UiTheme.ROW_H - 1, x + w, y + UiTheme.ROW_H, UiTheme.LINE);

        int textColor = rule.enabled ? UiTheme.WHITE : UiTheme.LINE;

        int toggleX = x + 2;
        drawBoxToggle(
                graphics, toggleX, y + 1, rule.enabled, hovered && hoveredButton == BTN_TOGGLE);

        int swatchX = toggleX + BTN + 3;
        if (styled) {
            int swatch = rule.enabled ? rule.color : Colors.withAlpha(rule.color, 0x80);
            graphics.fill(swatchX, y + 1, swatchX + SWATCH, y + 1 + SWATCH, swatch);
            graphics.outline(
                    swatchX,
                    y + 1,
                    SWATCH,
                    SWATCH,
                    hovered && hoveredButton == BTN_SWATCH ? UiTheme.ACCENT : UiTheme.LINE);
        }

        int nameX = styled ? swatchX + SWATCH + 5 : swatchX + 2;
        int nameY = y + (UiTheme.ROW_H - 8) / 2 + 1;
        int rightLimit = buttonX(x, w, 3) - 4;
        int available = Math.max(20, rightLimit - nameX);
        String summary = rule.error() != null ? "⚠ " + rule.error() : rule.filterSummary();
        int reserved = 0;
        if (!summary.isEmpty()) {
            reserved = Math.min(font.width(summary) + 6, available * 2 / 5);
            if (available - reserved < MIN_NAME_W) reserved = 0;
        }
        String name = font.plainSubstrByWidth(rule.displayName(), available - reserved);
        int nameColor = rule.error() != null ? UiTheme.WARN : textColor;
        graphics.text(font, name, nameX, nameY, nameColor, false);

        if (reserved > 0) {
            int sumX = nameX + font.width(name) + 6;
            int maxSum = rightLimit - sumX;
            if (maxSum > 12) {
                graphics.text(
                        font,
                        font.plainSubstrByWidth(summary, maxSum),
                        sumX,
                        nameY,
                        UiTheme.LINE,
                        false);
            }
        }

        int delX = buttonX(x, w, 0);
        int editX = buttonX(x, w, 1);
        int downX = buttonX(x, w, 2);
        int upX = buttonX(x, w, 3);
        drawIconButton(graphics, editX, y + 1, false, hovered && hoveredButton == BTN_EDIT, false);
        drawIconButton(
                graphics,
                delX,
                y + 1,
                true,
                hovered && hoveredButton == BTN_DELETE,
                pressedButton == BTN_DELETE
                        && pressedRow == index
                        && hovered
                        && hoveredButton == BTN_DELETE);
        drawArrowButton(graphics, upX, y + 1, true, hovered && hoveredButton == BTN_UP, index > 0);
        drawArrowButton(
                graphics,
                downX,
                y + 1,
                false,
                hovered && hoveredButton == BTN_DOWN,
                index < rules.size() - 1);
    }

    private static int buttonX(int x, int w, int slot) {
        return x + w - BTN - GAP - slot * (BTN + GAP);
    }

    private void drawBoxToggle(
            GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean hovered) {
        graphics.fill(x, y, x + BTN, y + BTN, UiTheme.MASK);
        graphics.fill(x + 4, y + 4, x + BTN - 4, y + BTN - 4, on ? UiTheme.ACCENT : UiTheme.MASK);
        graphics.outline(x, y, BTN, BTN, hovered ? UiTheme.ACCENT : UiTheme.LINE);
    }

    private void drawArrowButton(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            boolean up,
            boolean hovered,
            boolean enabled) {
        int border = hovered && enabled ? UiTheme.ACCENT : UiTheme.LINE;
        Graphics.fillSurface(graphics, x, y, x + BTN, y + BTN, UiTheme.MASK);
        graphics.outline(x, y, BTN, BTN, border);
        Graphics.drawChevron(
                graphics, x + BTN / 2, y + BTN / 2, enabled ? UiTheme.WHITE : UiTheme.LINE, up);
    }

    private void drawIconButton(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            boolean danger,
            boolean hovered,
            boolean pressed) {
        int bg =
                pressed
                        ? UiTheme.DANGER_SOLID
                        : (hovered && danger ? UiTheme.DANGER : UiTheme.MASK);
        int border =
                pressed
                        ? UiTheme.DANGER_SOLID
                        : (hovered
                                ? (danger ? UiTheme.DANGER_SOLID : UiTheme.ACCENT)
                                : UiTheme.LINE);
        Graphics.fillSurface(graphics, x, y, x + BTN, y + BTN, bg);
        graphics.outline(x, y, BTN, BTN, border);
        int cx = x + BTN / 2;
        int cy = y + BTN / 2;
        if (danger) Graphics.drawX(graphics, cx - 4, cy - 4, 8, UiTheme.WHITE);
        else Graphics.drawDots(graphics, cx, cy, UiTheme.WHITE);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (maxScroll > 0 && Scrollbar.hit(getX() + width, contentY(), contentH(), mx, my)) {
            draggingScrollbar = true;
            scroll = Scrollbar.scrollAt(contentY(), contentH(), maxScroll, my);
            return;
        }
        int row = rowAt(mx, my);
        if (row < 0) return;
        switch (buttonAt(mx, my)) {
            case BTN_EDIT, BTN_SWATCH -> callbacks.onEdit(row);
            case BTN_DELETE -> {
                pressedRow = row;
                pressedButton = BTN_DELETE;
            }
            case BTN_TOGGLE -> callbacks.onToggle(row);
            case BTN_UP -> callbacks.onMove(row, -1);
            case BTN_DOWN -> callbacks.onMove(row, 1);
            default -> {}
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        draggingScrollbar = false;
        if (pressedButton == BTN_DELETE && pressedRow >= 0) {
            int row = rowAt(event.x(), event.y());
            if (row == pressedRow
                    && buttonAt(event.x(), event.y()) == BTN_DELETE
                    && pressedRow < rules.size()) {
                callbacks.onDelete(pressedRow);
            }
        }
        pressedButton = BTN_NONE;
        pressedRow = -1;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar) {
            scroll = Scrollbar.scrollAt(contentY(), contentH(), maxScroll, event.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!isHovered) return false;
        clampScroll();
        scroll = Mth.clamp((int) (scroll - scrollY * 12), 0, maxScroll);
        return true;
    }

    public int scroll() {
        return scroll;
    }

    public void setScroll(int value) {
        clampScroll();
        scroll = Mth.clamp(value, 0, maxScroll);
    }

    @Override
    protected void updateWidgetNarration(
            net.minecraft.client.gui.narration.NarrationElementOutput output) {}

    private int rowAt(double mx, double my) {
        if (mx < getX() || mx >= getX() + width || my < contentY() || my >= contentY() + contentH())
            return -1;
        int index = (int) ((my - contentY() + scroll) / UiTheme.ROW_H);
        return index >= 0 && index < rules.size() ? index : -1;
    }

    private int buttonAt(double mx, double my) {
        int row = rowAt(mx, my);
        if (row < 0) return BTN_NONE;
        int y = getY() + 2 + row * UiTheme.ROW_H - scroll;
        int x = getX() + 1;
        int w = width - 6;
        if (my < y + 1 || my > y + BTN - 1) return BTN_NONE;
        int toggleX = x + 2;
        if (mx >= toggleX && mx <= toggleX + BTN) return BTN_TOGGLE;
        int swatchX = toggleX + BTN + 3;
        if (styled && mx >= swatchX && mx <= swatchX + SWATCH) return BTN_SWATCH;
        if (inSlot(mx, buttonX(x, w, 0))) return BTN_DELETE;
        if (inSlot(mx, buttonX(x, w, 1))) return BTN_EDIT;
        if (inSlot(mx, buttonX(x, w, 2)) && row < rules.size() - 1) return BTN_DOWN;
        if (inSlot(mx, buttonX(x, w, 3)) && row > 0) return BTN_UP;
        return BTN_NONE;
    }

    private static boolean inSlot(double mx, int slotX) {
        return mx >= slotX && mx <= slotX + BTN;
    }
}
