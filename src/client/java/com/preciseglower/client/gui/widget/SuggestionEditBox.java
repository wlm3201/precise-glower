package com.preciseglower.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.gui.autocomplete.Suggestion;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SuggestionEditBox extends EditBox {
    public interface Source {
        List<Suggestion> suggest(String text, int cursor);
    }

    private static final int ROW_H = 11;
    private static final int MAX_VISIBLE = 12;

    private Source source;
    private List<Suggestion> items = List.of();
    private int selectedIndex = -1;

    private int popupScroll;

    private boolean draggingPopupBar;
    private boolean popupOpen;
    private boolean suppressRefresh;

    public SuggestionEditBox(
            Font font, int x, int y, int width, int height, Component narration, Source source) {
        super(font, x, y, width, height, narration);
        this.source = source;
    }

    public void setSource(Source source) {
        this.source = source;
        refreshSuggestions();
    }

    public void refreshSuggestions() {
        if (suppressRefresh) return;
        if (source == null) {
            items = List.of();
            selectedIndex = -1;
            popupOpen = false;
            return;
        }
        items = source.suggest(getValue(), getCursorPosition());
        selectedIndex = items.isEmpty() ? -1 : 0;
        popupScroll = 0;
        popupOpen = !items.isEmpty();
    }

    public void closePopup() {
        popupOpen = false;
    }

    public boolean isPopupOpen() {
        return popupOpen;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popupOpen && !items.isEmpty()) {
            int key = event.key();
            if (key == InputConstants.KEY_DOWN) {
                selectedIndex = (selectedIndex + 1) % items.size();
                return true;
            }
            if (key == InputConstants.KEY_UP) {
                selectedIndex = (selectedIndex - 1 + items.size()) % items.size();
                return true;
            }
            if (key == InputConstants.KEY_TAB) {
                apply(selectedIndex < 0 ? 0 : selectedIndex);
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                popupOpen = false;
                return true;
            }
            if (key == InputConstants.KEY_RETURN) {
                apply(selectedIndex < 0 ? 0 : selectedIndex);
                return true;
            }
        }
        boolean handled = super.keyPressed(event);
        refreshSuggestions();
        return handled;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        boolean handled = super.charTyped(event);
        refreshSuggestions();
        return handled;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) popupOpen = false;
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event, doubleClick);
        refreshSuggestions();
    }

    private void apply(int index) {
        if (index < 0 || index >= items.size()) return;
        Suggestion suggestion = items.get(index);
        String text = getValue();
        int from = Math.max(0, Math.min(suggestion.replaceFrom(), text.length()));
        int to = Math.max(from, Math.min(suggestion.replaceTo(), text.length()));
        String next = text.substring(0, from) + suggestion.text() + text.substring(to);

        suppressRefresh = true;
        setValue(next);
        int cursor = from + suggestion.text().length();
        setCursorPosition(cursor);
        setHighlightPos(cursor);
        suppressRefresh = false;
        popupOpen = false;
    }

    public int popupHeight() {
        if (!popupOpen || items.isEmpty()) return 0;
        return Math.min(items.size(), MAX_VISIBLE) * ROW_H + 2;
    }

    public void renderPopup(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (!popupOpen || items.isEmpty()) return;
        int visible = Math.min(items.size(), MAX_VISIBLE);
        int x = getX();
        int y = getY() + getHeight() + 1;
        int w = getWidth();
        int h = visible * ROW_H + 2;

        graphics.fill(x, y, x + w, y + h, UiTheme.BLACK);
        graphics.outline(x, y, w, h, UiTheme.LINE);

        boolean scrollbar = items.size() > visible;
        int contentW = scrollbar ? w - Scrollbar.HOTSPOT_W : w;
        if (selectedIndex < popupScroll) popupScroll = selectedIndex;
        else if (selectedIndex >= popupScroll + visible) popupScroll = selectedIndex - visible + 1;
        popupScroll = Mth.clamp(popupScroll, 0, Math.max(0, items.size() - visible));

        for (int i = 0; i < visible; i++) {
            int index = popupScroll + i;
            Suggestion suggestion = items.get(index);
            int rowY = y + 1 + i * ROW_H;
            boolean hovered =
                    mouseX >= x && mouseX < x + contentW && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (index == selectedIndex) {
                graphics.fill(x + 1, rowY, x + contentW - 1, rowY + ROW_H, UiTheme.ACCENT_SOFT);
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + contentW - 1, rowY + ROW_H, UiTheme.LINE);
            }
            String label = font.plainSubstrByWidth(suggestion.text(), contentW - 12);
            graphics.text(font, label, x + 4, rowY + 2, UiTheme.WHITE, false);
            if (suggestion.hint() != null && !suggestion.hint().isEmpty()) {
                String hint = suggestion.hint();
                int hintX = x + contentW - 3 - font.width(hint);
                if (hintX > x + font.width(label) + 4) {
                    graphics.text(font, hint, hintX, rowY + 2, UiTheme.WHITE, false);
                }
            }
        }

        Scrollbar.draw(
                graphics,
                x + w,
                y + 1,
                visible * ROW_H,
                popupScroll * ROW_H,
                popupMaxScroll(),
                draggingPopupBar);
    }

    public boolean popupMouseClicked(double mouseX, double mouseY) {
        if (!popupOpen || items.isEmpty()) return false;
        int visible = Math.min(items.size(), MAX_VISIBLE);
        int x = getX();
        int y = getY() + getHeight() + 1;
        int w = getWidth();
        int h = visible * ROW_H + 2;
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;
        if (items.size() > visible
                && Scrollbar.hit(x + w, y + 1, visible * ROW_H, mouseX, mouseY)) {
            draggingPopupBar = true;
            popupScrollTo(mouseY);
            return true;
        }
        int index = popupScroll + (int) ((mouseY - y - 1) / ROW_H);
        if (index >= 0 && index < items.size()) apply(index);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPopupBar) {
            popupScrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        draggingPopupBar = false;
        super.onRelease(event);
    }

    public boolean popupMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!popupOpen || items.isEmpty()) return false;
        int visible = Math.min(items.size(), MAX_VISIBLE);
        if (items.size() <= visible) return false;
        int x = getX();
        int y = getY() + getHeight() + 1;
        int h = visible * ROW_H + 2;
        if (mouseX < x || mouseX >= x + getWidth() || mouseY < y || mouseY >= y + h) return false;
        clampPopupScroll(popupScroll - (int) scrollY * 3);
        return true;
    }

    public boolean isDraggingPopupBar() {
        return draggingPopupBar;
    }

    private void popupScrollTo(double mouseY) {
        int visible = Math.min(items.size(), MAX_VISIBLE);
        int y = getY() + getHeight() + 1;
        int scrollPx = Scrollbar.scrollAt(y + 1, visible * ROW_H, popupMaxScroll(), mouseY);
        clampPopupScroll(scrollPx / ROW_H);
    }

    private int popupMaxScroll() {
        int visible = Math.min(items.size(), MAX_VISIBLE);
        return Math.max(0, items.size() - visible) * ROW_H;
    }

    private void clampPopupScroll(int value) {
        int visible = Math.min(items.size(), MAX_VISIBLE);
        popupScroll = Mth.clamp(value, 0, Math.max(0, items.size() - visible));
        if (selectedIndex >= 0 && !items.isEmpty()) {
            selectedIndex =
                    Mth.clamp(
                            selectedIndex,
                            popupScroll,
                            Math.max(popupScroll, popupScroll + visible - 1));
        }
    }
}
