package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class Scrollbar {
    public static final int HOTSPOT_W = 6;

    private static final int BAR_W = 3;
    private static final int MIN_THUMB = 14;

    private static final int INSET = 5;

    private Scrollbar() {}

    public static int thumbHeight(int viewH, int maxScroll) {
        if (viewH <= 0) return MIN_THUMB;
        return Math.max(MIN_THUMB, viewH * viewH / (viewH + Math.max(0, maxScroll)));
    }

    public static boolean hit(int right, int top, int height, double mx, double my) {
        return mx >= right - HOTSPOT_W && mx < right && my >= top && my < top + height;
    }

    public static int scrollAt(int top, int height, int maxScroll, double mouseY) {
        int thumbH = thumbHeight(height, maxScroll);
        int track = height - thumbH;
        if (maxScroll <= 0 || track <= 0) return 0;
        float t = Mth.clamp((float) (mouseY - top - thumbH / 2.0) / track, 0.0F, 1.0F);
        return Mth.clamp((int) (t * maxScroll), 0, maxScroll);
    }

    public static void draw(
            GuiGraphicsExtractor graphics,
            int right,
            int top,
            int height,
            int scroll,
            int maxScroll,
            boolean dragging) {
        if (maxScroll <= 0) return;
        int thumbH = thumbHeight(height, maxScroll);
        int thumbY = top + (height - thumbH) * scroll / maxScroll;
        int barX = right - INSET;
        graphics.fill(barX, top, barX + BAR_W, top + height, UiTheme.LINE);
        graphics.fill(
                barX,
                thumbY,
                barX + BAR_W,
                thumbY + thumbH,
                dragging ? UiTheme.ACCENT : UiTheme.WHITE);
    }
}
