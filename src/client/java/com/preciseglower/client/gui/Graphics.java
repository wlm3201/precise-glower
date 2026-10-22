package com.preciseglower.client.gui;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.GlowerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Graphics {
    private Graphics() {}

    public static boolean hovered(
            int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void fillSurface(
            GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (color == UiTheme.MASK) return;
        graphics.fill(x0, y0, x1, y1, color);
    }

    public static void drawX(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            graphics.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, color);
        }
    }

    public static void drawDots(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
        graphics.fill(cx - 5, cy, cx - 4, cy + 1, color);
        graphics.fill(cx - 1, cy, cx, cy + 1, color);
        graphics.fill(cx + 3, cy, cx + 4, cy + 1, color);
    }

    public static void drawChevron(
            GuiGraphicsExtractor graphics, int cx, int cy, int color, boolean up) {
        int half = 3;
        int top = cy - 2;
        for (int i = 0; i <= half; i++) {
            int leftX = cx - half + i;
            int leftY = up ? top + half - i : top + i;
            graphics.fill(leftX, leftY, leftX + 1, leftY + 1, color);
            if (i == 0) continue;
            int rightX = cx + i;
            int rightY = up ? top + i : top + half - i;
            graphics.fill(rightX, rightY, rightX + 1, rightY + 1, color);
        }
    }

    public static void tabs(
            GuiGraphicsExtractor graphics,
            Font font,
            int width,
            GlowerScreen.Tab tab,
            int mouseX,
            int mouseY) {
        int y = 0;
        graphics.fill(0, y + UiTheme.TAB_H, width, y + UiTheme.TAB_H + 1, UiTheme.LINE);

        int cx = width - UiTheme.TAB_H;
        boolean closeHover = hovered(cx, 0, UiTheme.TAB_H, UiTheme.TAB_H, mouseX, mouseY);
        graphics.outline(
                cx,
                y,
                UiTheme.TAB_H,
                UiTheme.TAB_H,
                closeHover ? UiTheme.DANGER_SOLID : UiTheme.LINE);
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
            graphics.fill(
                    x + UiTheme.TAB_W, y, x + UiTheme.TAB_W + 1, y + UiTheme.TAB_H, UiTheme.LINE);

            String label =
                    Component.translatable(
                                    switch (t) {
                                        case BLOCK -> "preciseglower.tab.blocks";
                                        case ENTITY -> "preciseglower.tab.entities";
                                        case ITEM -> "preciseglower.tab.items";
                                        case HIDE_ENTITY -> "preciseglower.tab.hide_entities";
                                        case HIDE_ITEM -> "preciseglower.tab.hide_items";
                                    })
                            .getString();
            int count =
                    switch (t) {
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

    public static void sideHeader(
            GuiGraphicsExtractor graphics, Font font, String title, int color) {
        int y = UiTheme.TAB_H;
        graphics.fill(
                0,
                y + UiTheme.LIST_HEADER_H,
                UiTheme.SIDE_W,
                y + UiTheme.LIST_HEADER_H + 1,
                UiTheme.LINE);
        int textY = y + (UiTheme.LIST_HEADER_H - 8) / 2;
        graphics.text(
                font, font.plainSubstrByWidth(title, UiTheme.SIDE_W - 10), 5, textY, color, false);
    }

    public static void mainHeader(
            GuiGraphicsExtractor graphics, Font font, int width, String title) {
        int y = UiTheme.TAB_H;
        int x = UiTheme.SIDE_W + 1;
        graphics.fill(
                x, y + UiTheme.LIST_HEADER_H, width, y + UiTheme.LIST_HEADER_H + 1, UiTheme.LINE);
        graphics.text(
                font, title, x + 5, y + (UiTheme.LIST_HEADER_H - 8) / 2, UiTheme.WHITE, false);
    }
}
