package com.preciseglower.client.gui.widget;

import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.gui.autocomplete.Candidate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

/** 左侧搜索结果列表：id + 右侧灰色的本地化名，支持键盘上下选择。 */
public class CandidateListWidget extends AbstractWidget {
	public interface Callbacks {
		void onPick(int index);

		void onCommit(int index);
	}

	private static final int ROW_H = 12;

	private final Minecraft mc = Minecraft.getInstance();
	private List<Candidate> entries = new ArrayList<>();
	private final Callbacks callbacks;
	private int scroll;
	private int maxScroll;
	private int hovered = -1;
	private int selected = -1;
	private boolean draggingScrollbar;

	public CandidateListWidget(int x, int y, int width, int height, Callbacks callbacks) {
		super(x, y, width, height, net.minecraft.network.chat.Component.empty());
		this.callbacks = callbacks;
	}

	public void setEntries(List<Candidate> entries) {
		this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
		this.scroll = 0;
	}

	public List<Candidate> entries() {
		return entries;
	}

	public int selected() {
		return selected;
	}

	public void setSelected(int index) {
		this.selected = index;
	}

	public int scroll() {
		return scroll;
	}

	/**
	 * 重建后恢复滚动位置（{@link #setEntries} 会把它清零）。
	 *
	 * <p>如果选中的那条不在恢复出来的视野里，就顺手把它带回来 —— 先顾滚动位置，
	 * 只在必要时才跳，避免「明明保留了滚动条却看不到聚焦的那条」。</p>
	 */
	public void restoreScroll(int value) {
		this.scroll = value;
		clampScroll();
		if (selected >= 0 && selected < entries.size()) ensureVisible(selected);
	}

	/** 键盘 ↑↓ 移动选择，并让选中项保持可见 */
	public void moveSelection(int delta) {
		if (entries.isEmpty()) {
			selected = -1;
			return;
		}
		int next = selected < 0 ? (delta > 0 ? 0 : entries.size() - 1) : selected + delta;
		selected = Mth.clamp(next, 0, entries.size() - 1);
		ensureVisible(selected);
	}

	private void ensureVisible(int index) {
		int visible = Math.max(1, (height - 2) / ROW_H);
		if (index * ROW_H < scroll) scroll = index * ROW_H;
		else if ((index + 1) * ROW_H > scroll + visible * ROW_H) {
			scroll = (index + 1) * ROW_H - visible * ROW_H;
		}
		clampScroll();
	}

	private void clampScroll() {
		maxScroll = Math.max(0, entries.size() * ROW_H - (height - 2));
		scroll = Mth.clamp(scroll, 0, maxScroll);
	}

	private int contentY() {
		return getY() + 1;
	}

	private int contentH() {
		return height - 2;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = mc.font;
		updateHovered(mouseX, mouseY);
		clampScroll();

		int contentX = getX() + 1;
		int contentY = contentY();
		int contentW = maxScroll > 0 ? width - 2 - Scrollbar.HOTSPOT_W : width - 2;
		int contentH = contentH();
		graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
		for (int i = 0; i < entries.size(); i++) {
			int rowY = contentY + i * ROW_H - scroll;
			if (rowY + ROW_H < contentY || rowY > contentY + contentH) continue;
			boolean isHover = hovered == i && isHovered;
			boolean isSelected = selected == i;
			if (isSelected) graphics.fill(contentX, rowY, contentX + contentW, rowY + ROW_H, UiTheme.ACCENT_SOFT);
			else if (isHover) graphics.fill(contentX, rowY, contentX + contentW, rowY + ROW_H, UiTheme.MASK);

			Candidate candidate = entries.get(i);
			int textColor = UiTheme.WHITE;
			// 本地名最多占 1/3 宽度，保证 id 永远有至少 2/3 的空间（否则 id 会被挤没）
			int labelW = 0;
			if (!candidate.label().isEmpty()) {
				labelW = Math.min(font.width(candidate.label()), contentW / 3);
			}
			int idW = contentW - 8 - (labelW > 0 ? labelW + 6 : 0);
			String idText = font.plainSubstrByWidth(candidate.id(), idW);
			graphics.text(font, idText, contentX + 4, rowY + 2, textColor, false);
			if (labelW > 0) {
				String label = font.plainSubstrByWidth(candidate.label(), labelW);
				graphics.text(font, label, contentX + contentW - 4 - font.width(label), rowY + 2, UiTheme.WHITE, false);
			}
		}
		graphics.disableScissor();

		Scrollbar.draw(graphics, getX() + width, contentY(), contentH(), scroll, maxScroll, draggingScrollbar);

		if (entries.isEmpty()) return;
		graphics.fill(getX(), getY(), getX() + width, getY() + 1, UiTheme.LINE);
	}

	private void updateHovered(double mouseX, double mouseY) {
		int contentY = contentY();
		int contentH = contentH();
		boolean overBar = Scrollbar.hit(getX() + width, contentY, contentH, mouseX, mouseY);
		if (isHovered && !overBar
			&& mouseX >= getX() && mouseX < getX() + width && mouseY >= contentY && mouseY < contentY + contentH) {
			int index = (int) ((mouseY - contentY + scroll) / ROW_H);
			hovered = index >= 0 && index < entries.size() ? index : -1;
		} else {
			hovered = -1;
		}
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
		if (hovered < 0 || hovered >= entries.size()) return;
		selected = hovered;
		callbacks.onPick(hovered);
		if (doubleClick) callbacks.onCommit(hovered);
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		draggingScrollbar = false;
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

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
	}
}
