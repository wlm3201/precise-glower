package com.preciseglower.client.gui.widget;

import com.mojang.logging.LogUtils;
import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.util.Colors;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class ColorPicker extends AbstractWidget {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CELL_W = 17;
    private static final int GAP = 2;

    private static final int CELL_H = UiTheme.CONTROL_H;

    private static final int PRESET_COLS = 8;
    private static final int PRESET_W = PRESET_COLS * (CELL_W + GAP);
    private static final int RIGHT_GAP = 6;

    private static final int COL_H = UiTheme.CONTROL_H;

    private static final int COL_GAP = 2;

    public static int preferredHeight() {
        return COL_H + COL_GAP + UiTheme.CONTROL_H;
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Consumer<Integer> onChanged;
    private final EditBox hexBox;
    private int color;
    private boolean syncing;

    private String lastHex;

    private String lastLoggedHex = "\u0000";

    public ColorPicker(int x, int y, int width, int color, Consumer<Integer> onChanged) {
        super(x, y, width, preferredHeight(), Component.empty());
        this.color = color;
        this.onChanged = onChanged;
        int rx = x + PRESET_W + RIGHT_GAP;
        int rw = width - PRESET_W - RIGHT_GAP;
        this.hexBox =
                new EditBox(
                        mc.font,
                        rx,
                        y + COL_H + COL_GAP,
                        rw,
                        UiTheme.CONTROL_H,
                        Component.literal("hex"));
        this.hexBox.setMaxLength(8);
        this.hexBox.setValue(Colors.toHex(color));
        this.lastHex = this.hexBox.getValue();
        this.hexBox.setResponder(s -> {});
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

    public void syncFromHexBox() {
        applyHex();
    }

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
        if (!text.equals(lastLoggedHex)) {
            lastLoggedHex = text;
            LOG.info(
                    "[ag-hex] text='{}' parsed={} colorNow={} applied={}",
                    text,
                    parsed.isPresent() ? String.format("%08X", parsed.getAsInt()) : "invalid",
                    String.format("%08X", color),
                    applied);
        }
    }

    public int bottom() {
        return getY() + getHeight();
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.accept(color);
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
            boolean hovered =
                    mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H;
            graphics.outline(
                    cx,
                    cy,
                    CELL_W,
                    CELL_H,
                    equalsColor(preset) || hovered ? UiTheme.WHITE : UiTheme.LINE);
        }

        int rx = getX() + PRESET_W + RIGHT_GAP;
        int rw = width - PRESET_W - RIGHT_GAP;
        for (int gx = 0; gx < rw; gx += 6) {
            for (int gy = 0; gy < COL_H; gy += 6) {
                int cw = Math.min(6, rw - gx);
                int ch = Math.min(6, COL_H - gy);
                boolean light = ((gx / 6) + (gy / 6)) % 2 == 0;
                graphics.fill(
                        rx + gx,
                        getY() + gy,
                        rx + gx + cw,
                        getY() + gy + ch,
                        light ? UiTheme.WHITE : UiTheme.BLACK);
            }
        }
        graphics.fill(rx, getY(), rx + rw, getY() + COL_H, color);
        graphics.outline(rx, getY(), rw, COL_H, UiTheme.LINE);

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
        if (hexBox.isFocused()) return hexBox.mouseDragged(event, dx, dy);
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (hexBox.isFocused()) hexBox.onRelease(event);
        super.onRelease(event);
    }

    @Override
    protected void updateWidgetNarration(
            net.minecraft.client.gui.narration.NarrationElementOutput output) {}
}
