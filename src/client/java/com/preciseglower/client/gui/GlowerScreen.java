package com.preciseglower.client.gui;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.config.BlockRule;
import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.GlowerConfig;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.gui.autocomplete.Candidate;
import com.preciseglower.client.gui.autocomplete.IdSuggester;
import com.preciseglower.client.gui.autocomplete.NameIndex;
import com.preciseglower.client.match.BlockStateFilter;
import com.preciseglower.client.util.AutoColor;
import com.preciseglower.client.gui.widget.CandidateListWidget;
import com.preciseglower.client.gui.widget.FlatButton;
import com.preciseglower.client.gui.widget.RuleEditModal;
import com.preciseglower.client.gui.widget.RuleListWidget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 主界面：左侧搜索（可搜 id，也可搜中文名），右侧规则列表，弹窗编辑。
 * 搜索框不做下拉补全：↑↓ 直接在结果列表里移动，Enter 加入规则。
 */
public class GlowerScreen extends Screen implements RuleEditModal.Host {
	/** 前三个是高亮规则，后两个是「隐藏渲染」规则（隐藏实体 / 隐藏物品） */
	public enum Tab { BLOCK, ENTITY, ITEM, HIDE_ENTITY, HIDE_ITEM }

	/** 弹窗打开时喂给底层控件的「鼠标在屏幕外」坐标，避免悬停态穿透 */
	private static final int OFFSCREEN = -1000;

	private final Screen parent;
	private Tab tab;

	private EditBox searchBox;
	private CandidateListWidget candidateList;
	private RuleListWidget ruleList;
	private final List<Candidate> candidates = new ArrayList<>();
	/** 重建界面后要不要把搜索框设成全选（添加完规则后保留内容 + 全选，见 {@link #addRule(String)}） */
	private boolean selectAllSearch;
	/** 重建界面后候选列表的高亮位置（见 {@link #changed()}） */
	private int keepCandidateIndex;
	/**
	 * 重建后候选列表的滚动位置。同样是在 {@link #changed()} 里现场存下来的 ——
	 * {@code CandidateListWidget#setEntries} 会把滚动清零，不存就会每次都跳回顶部。
	 */
	private int keepCandidateScroll;
	/** 重建后规则栏的滚动位置：删除条目、编辑保存之后不该跳回顶部 */
	private int keepRuleScroll;
	/**
	 * 下一帧要不要把焦点抢回搜索框（添加规则后）。
	 *
	 * <p>不能在 {@link #buildUi()} 里当场抢：点列表项时 {@code Screen#mouseClicked} 会把焦点
	 * 交给「处理了这次点击的控件」，而我们是<b>在点击回调里</b>重建界面的，那个控件此时已经
	 * 不在 children 里了 —— 当场 {@code setFocused} 随后就会被这次焦点切换冲掉，
	 * 表现为「内容保留了、也全选了，但搜索框没焦点，得手动点一下，一点又没了全选」。</p>
	 *
	 * <p>所以放到 {@link #tick()} 里，等这一帧的输入事件处理完再抢。</p>
	 */
	private boolean focusSearchPending;
	/**
	 * 是否已经构建过一次界面。
	 *
	 * <p>{@code setInitialFocus} 是<b>当场生效</b>的，所以只能留在首次构建：
	 * 重建（删除 / 开关 / 上下移动 / 切页）时再调一次，就会把焦点硬抢回搜索框 ——
	 * 点删除按钮之后焦点跑到输入框就是这么来的。</p>
	 */
	private boolean builtOnce;

	private RuleEditModal modal;
	private String keepSearch = "";
	/** 左侧栏标题位置的临时提示（添加失败时显示，输入或切页即清） */
	private @Nullable String notice;

	public GlowerScreen(Screen parent) {
		super(Component.translatable("precise-glower.title"));
		this.parent = parent;
		GlowerConfig config = PreciseGlowerClient.config();
		this.tab = parseTab(config == null ? null : config.lastTab);
	}

	private static Tab parseTab(String value) {
		if (value != null) {
			for (Tab t : Tab.values()) if (t.name().equals(value)) return t;
		}
		return Tab.BLOCK;
	}

	// ------------------------------------------------------------------- build

	@Override
	protected void init() {
		NameIndex.rebuild();
		buildUi();
	}

	private void buildUi() {
		int sideX = 6;
		int candW = UiTheme.SIDE_W - 12;
		int searchY = UiTheme.TAB_H + UiTheme.LIST_HEADER_H + 6;
		int searchW = candW - 52;

		searchBox = new EditBox(font, sideX, searchY, searchW, UiTheme.CONTROL_H,
			Component.translatable(searchKey()));
		searchBox.setMaxLength(256);
		searchBox.setValue(keepSearch);
		searchBox.setHint(Component.translatable("precise-glower.search.hint"));
		searchBox.setResponder(s -> {
			keepSearch = s;
			notice = null;
			// 换了查询词就从头开始：高亮回到第一条
			refreshCandidates(0);
		});
		addRenderableWidget(searchBox);

		addRenderableWidget(new FlatButton(sideX + searchW + 4, searchY, 48, UiTheme.CONTROL_H,
			Component.translatable("precise-glower.add"), FlatButton.Style.PRIMARY, b -> addRule(null)));

		candidateList = new CandidateListWidget(sideX, searchY + UiTheme.CONTROL_H + 4, candW,
			height - (searchY + UiTheme.CONTROL_H + 4) - 6, new CandidateListWidget.Callbacks() {
				@Override
				public void onPick(int index) {
					// 鼠标左键 = 直接添加（与 Enter 一致）
					if (index >= 0 && index < candidates.size()) addRule(candidates.get(index).id());
				}

				@Override
				public void onCommit(int index) {
					if (index >= 0 && index < candidates.size()) addRule(candidates.get(index).id());
				}
			});
		addRenderableWidget(candidateList);

		int mainX = UiTheme.SIDE_W + 4;
		int listW = width - mainX - 4;
		int listY = UiTheme.TAB_H + UiTheme.LIST_HEADER_H;
		int listH = height - listY - 6;

		ruleList = new RuleListWidget(mainX, listY, listW, listH, rules(), new RuleListWidget.Callbacks() {
			@Override
			public void onEdit(int index) {
				openEditor(index);
			}

			@Override
			public void onDelete(int index) {
				rules().remove(index);
				changed();
			}

			@Override
			public void onToggle(int index) {
				Rule rule = rules().get(index);
				rule.enabled = !rule.enabled;
				changed();
			}

			@Override
			public void onMove(int index, int delta) {
				moveRule(index, delta);
			}
		}, styledTab());
		addRenderableWidget(ruleList);

		int headerY = UiTheme.TAB_H + 2;
		// 按钮宽度按文案实际宽度算，中英文都不至于把字挤出去
		int clearW = font.width(Component.translatable("precise-glower.clear").getString()) + 10;
		int disableW = font.width(Component.translatable("precise-glower.disable_all").getString()) + 10;
		int enableW = font.width(Component.translatable("precise-glower.enable_all").getString()) + 10;
		int btnRight = width - 4;
		addRenderableWidget(new FlatButton(btnRight - clearW, headerY, clearW, UiTheme.CONTROL_H,
			Component.translatable("precise-glower.clear"), FlatButton.Style.DANGER, b -> clearRules())
			.requireConfirm());
		btnRight -= clearW + 4;
		addRenderableWidget(new FlatButton(btnRight - disableW, headerY, disableW, UiTheme.CONTROL_H,
			Component.translatable("precise-glower.disable_all"), FlatButton.Style.NORMAL, b -> setAllEnabled(false))
			.hoverBorder(UiTheme.DANGER_SOLID).hoverText(UiTheme.DANGER_SOLID));
		btnRight -= disableW + 4;
		addRenderableWidget(new FlatButton(btnRight - enableW, headerY, enableW, UiTheme.CONTROL_H,
			Component.translatable("precise-glower.enable_all"), FlatButton.Style.NORMAL, b -> setAllEnabled(true))
			.hoverBorder(UiTheme.ACCENT).hoverText(UiTheme.ACCENT));

		refreshCandidates(keepCandidateIndex);
		// 滚动要等条目装好再恢复（setEntries 会清零，且恢复时要按新内容重新夹一次）
		if (candidateList != null) candidateList.restoreScroll(keepCandidateScroll);
		if (ruleList != null) ruleList.setScroll(keepRuleScroll);
		// 只有首次构建才把焦点放到搜索框；重建不抢焦点，
		// 添加完之后要的焦点由 focusSearchPending 在下一帧显式去拿（见该字段的说明）
		if (!builtOnce) {
			builtOnce = true;
			setInitialFocus(searchBox);
		}
		// 选区在这里铺好，焦点由 tick 去抢（见 focusSearchPending 的说明）
		if (selectAllSearch && !keepSearch.isEmpty()) {
			searchBox.setHighlightPos(0);
			searchBox.moveCursorToEnd(true);
		} else {
			searchBox.moveCursorToEnd(false);
		}
		selectAllSearch = false;
		keepCandidateIndex = 0;
		keepCandidateScroll = 0;
		keepRuleScroll = 0;
	}

	private String searchKey() {
		// 五个页签的搜索框标题文案一样，共用一个键
		return "precise-glower.search.title";
	}

	/** 隐藏规则没有颜色 / 线宽之类的外观参数 */
	private boolean styledTab() {
		return tab == Tab.BLOCK || tab == Tab.ENTITY || tab == Tab.ITEM;
	}

	/** id 查不到时的文案按当前页走，带上 id 本身才好认 */
	private String unknownKey() {
		return switch (tab) {
			case BLOCK -> "precise-glower.error.unknown_block";
			case ENTITY, HIDE_ENTITY -> "precise-glower.error.unknown_entity";
			case ITEM, HIDE_ITEM -> "precise-glower.error.unknown_item";
		};
	}

	private List<? extends Rule> rules() {
		GlowerConfig config = PreciseGlowerClient.config();
		return switch (tab) {
			case BLOCK -> config.blocks;
			case ENTITY -> config.entities;
			case ITEM -> config.items;
			case HIDE_ENTITY -> config.hiddenEntities;
			case HIDE_ITEM -> config.hiddenItems;
		};
	}

	/**
	 * @param selectIndex 重建后要把高亮停在第几条；添加完规则后要留在原位，好让用户连着按回车
	 *                    （见 {@link #keepCandidateIndex}）
	 */
	private void refreshCandidates(int selectIndex) {
		String query = searchBox == null ? "" : searchBox.getValue().trim();
		List<Candidate> result = switch (tab) {
			case BLOCK -> IdSuggester.blocks(query);
			case ENTITY, HIDE_ENTITY -> IdSuggester.entities(query);
			case ITEM, HIDE_ITEM -> IdSuggester.items(query);
		};
		candidates.clear();
		candidates.addAll(result);
		if (candidateList != null) {
			candidateList.setEntries(candidates);
			int index = candidates.isEmpty() ? -1 : Math.max(0, Math.min(selectIndex, candidates.size() - 1));
			candidateList.setSelected(index);
		}
	}

	// ------------------------------------------------------------------ actions

	/**
	 * @param explicit 点列表项 / 回车时使用的高亮条目；为 null 表示「按搜索框里自己打的词添加」
	 */
	private void addRule(@Nullable String explicit) {
		// 查询词始终是用户自己打的那个：点列表项只是挑中其中一条，
		// 不该把条目 id 灌进搜索框（搜 boat 点 cherry_boat 之后，框里还得是 boat）
		String query = searchBox == null ? "" : searchBox.getValue().trim();
		String text = explicit != null ? explicit : query;
		if (text.isEmpty()) {
			notice = Component.translatable("precise-glower.status.empty_id").getString();
			return;
		}
		GlowerConfig config = PreciseGlowerClient.config();
		notice = null;
		// 方块标签来自服务器下发的数据，主菜单里查不到：给一句明确的提示而不是「未知方块」
		if (tab == Tab.BLOCK && text.startsWith("#") && !BlockStateFilter.tagsAvailable()) {
			notice = Component.translatable("precise-glower.error.tag_needs_world", text).getString();
			return;
		}
		// id 不在本类注册表里就明确说一声，不要静默什么都不做
		boolean known = switch (tab) {
			case BLOCK -> IdSuggester.blockExists(text);
			case ENTITY, HIDE_ENTITY -> IdSuggester.entityExists(text);
			case ITEM, HIDE_ITEM -> IdSuggester.itemExists(text);
		};
		if (!known) {
			notice = Component.translatable(unknownKey(), text).getString();
			return;
		}
		// 不做去重：同一个 id 允许出现多条规则，靠状态（方块）/ NBT（实体、物品）区分，
		// 于是同一个目标的不同状态可以各占一行、各用一种颜色
		switch (tab) {
			case BLOCK -> {
				BlockRule rule = new BlockRule();
				rule.id = text;
				rule.color = AutoColor.forBlock(text);
				insertByPriority(config.blocks, rule);
			}
			case ENTITY -> {
				EntityRule rule = new EntityRule();
				rule.type = text;
				rule.color = AutoColor.forEntity(text);
				insertByPriority(config.entities, rule);
			}
			case ITEM -> {
				ItemRule rule = new ItemRule();
				rule.item = text;
				rule.color = AutoColor.forItem(text);
				insertByPriority(config.items, rule);
			}
			case HIDE_ENTITY -> {
				EntityRule rule = new EntityRule();
				rule.type = text;
				config.hiddenEntities.add(0, rule);
			}
			case HIDE_ITEM -> {
				ItemRule rule = new ItemRule();
				rule.item = text;
				config.hiddenItems.add(0, rule);
			}
		}
		// 不清空：保留查询词，候选列表也就还是原来那一批，方便连着加同类目标
		keepSearch = query;
		// 添加完一律把搜索框设为「有焦点 + 全选」：直接打字就整段替换，
		// 不打字则上次的候选结果还在（点列表项和回车两种入口都一视同仁）
		selectAllSearch = !query.isEmpty();
		focusSearchPending = !query.isEmpty();
		// 高亮与滚动由 changed() 统一存（删除 / 开关那些操作也要保留）
		changed();
	}

	/**
	 * 新规则的落位：插到第一条「不比它更具体」的规则之前（也就是本档的最前面）。
	 *
	 * <p>列表下标就是生效优先级，直接塞在 0 会让一条新加的通配规则压住所有具体规则；
	 * 用 {@code <=} 而不是 {@code <}，是为了让同级里新添的那条排最前 —— 与原来
	 * 「按 {@link Rule#specificity()} 稳定排序」的结果一致。之后用户手工调的顺序才是最终顺序。</p>
	 */
	private static <T extends Rule> void insertByPriority(List<T> list, T rule) {
		int score = rule.specificity();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).specificity() <= score) {
				list.add(i, rule);
				return;
			}
		}
		list.add(rule);
	}

	/**
	 * 上下移动一条规则：直接换 {@code config} 里那条 List 的元素位置。
	 *
	 * <p>这个 List 就是各追踪器手里的同一份（{@code setRules} 存的是引用），它们按下标决定
	 * 「谁压住谁」，所以换位置 = 换生效优先级；随后 {@link #changed()} 重新解析表达式、
	 * 让追踪器按下标重算每规则的范围与命中数，并把新顺序写进配置文件。</p>
	 */
	private void moveRule(int index, int delta) {
		List<? extends Rule> list = rules();
		int to = index + delta;
		if (index < 0 || to < 0 || index >= list.size() || to >= list.size()) return;
		// changed() 会整体重建 UI（列表悬停 / 滚动都会丢），先存好滚动量再还原
		Collections.swap(list, index, to);
		changed();
	}

	private void openEditor(int index) {
		Rule source = rules().get(index);
		Rule copy = source.copy();
		RuleEditModal.Kind kind = switch (tab) {
			case BLOCK -> RuleEditModal.Kind.BLOCK;
			case ENTITY -> RuleEditModal.Kind.ENTITY;
			case ITEM -> RuleEditModal.Kind.ITEM;
			case HIDE_ENTITY -> RuleEditModal.Kind.HIDE_ENTITY;
			case HIDE_ITEM -> RuleEditModal.Kind.HIDE_ITEM;
		};
		modal = new RuleEditModal(this, kind, copy, saved -> {
			if (saved) {
				replaceRule(index, copy);
				changed();
			}
			modal = null;
		});
		modal.init(font);
	}

	private void replaceRule(int index, Rule rule) {
		GlowerConfig config = PreciseGlowerClient.config();
		switch (tab) {
			case BLOCK -> config.blocks.set(index, (BlockRule) rule);
			case ENTITY -> config.entities.set(index, (EntityRule) rule);
			case ITEM -> config.items.set(index, (ItemRule) rule);
			case HIDE_ENTITY -> config.hiddenEntities.set(index, (EntityRule) rule);
			case HIDE_ITEM -> config.hiddenItems.set(index, (ItemRule) rule);
		}
	}

	/**
	 * 规则有变动：重新解析规则并落盘，然后<strong>重绘整个界面</strong>。
	 *
	 * <p>重绘意味着所有控件都是新的，搜索框的光标 / 选区、候选列表的高亮位置全都会丢。
	 * 要靠 {@link #keepSearch}、{@link #selectAllSearch}、{@link #keepCandidateIndex}
	 * 这三个字段把状态带过重建（在 {@link #buildUi()} 末尾恢复）。想改「添加之后输入框是什么状态」，
	 * 改的是这三个字段，不是控件本身。</p>
	 */
	private void changed() {
		PreciseGlowerClient.refreshRules();
		PreciseGlowerClient.saveConfig();
		// 重建会丢掉控件的滚动与高亮：所有会触发重建的操作
		// （添加 / 删除 / 编辑保存 / 开关 / 移动 / 启用禁用 / 清空）都在这里先存下来，
		// 由 buildUi 在新控件建好之后恢复
		keepCandidateIndex = candidateList == null ? 0 : Math.max(0, candidateList.selected());
		keepCandidateScroll = candidateList == null ? 0 : candidateList.scroll();
		keepRuleScroll = ruleList == null ? 0 : ruleList.scroll();
		clearWidgets();
		buildUi();
	}

	private void switchTab(Tab next) {
		if (next == tab) return;
		tab = next;
		GlowerConfig config = PreciseGlowerClient.config();
		if (config != null) config.lastTab = tab.name();
		modal = null;
		keepSearch = "";
		notice = null;
		clearWidgets();
		buildUi();
	}

	private void setAllEnabled(boolean enabled) {
		for (Rule rule : rules()) rule.enabled = enabled;
		changed();
	}

	private void clearRules() {
		rules().clear();
		changed();
	}

	// ------------------------------------------------------------------ render

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// 整个界面就是一块遮罩：列表 / 表头 / tab 栏都不再各自铺底色，margin 不透出游戏画面
		graphics.fill(0, 0, width, height, UiTheme.MASK);
		// 搜索栏与规则栏之间的分割线：从表头一直拉到屏幕底部
		graphics.fill(UiTheme.SIDE_W, UiTheme.TAB_H, UiTheme.SIDE_W + 1, height, UiTheme.LINE);

		Graphics.tabs(graphics, font, width, tab, modal == null ? mouseX : OFFSCREEN, modal == null ? mouseY : OFFSCREEN);
		if (notice == null) Graphics.sideHeader(graphics, font, Component.translatable(searchKey()).getString());
		else Graphics.sideHeader(graphics, font, notice, UiTheme.WARN);
		Graphics.mainHeader(graphics, font, width, Component.translatable("precise-glower.rules").getString());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// 弹窗打开时，鼠标不该再穿透影响后面面板的悬停态：给它一组屏幕外的坐标
		int behindX = modal == null ? mouseX : OFFSCREEN;
		int behindY = modal == null ? mouseY : OFFSCREEN;
		super.extractRenderState(graphics, behindX, behindY, partialTick);
		if (modal != null) {
			graphics.nextStratum();
			modal.render(graphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (modal != null) modal.tick();
		if (focusSearchPending) {
			focusSearchPending = false;
			// 弹窗打开时不抢焦点；焦点要在选区铺好之后再给，顺序反了可能把选区清掉
			if (modal == null && searchBox != null) {
				setFocused(searchBox);
				searchBox.setHighlightPos(0);
				searchBox.moveCursorToEnd(true);
			}
		}
	}

	// ------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (modal != null) return modal.mouseClicked(event, doubleClick);

		if (my >= 0 && my < UiTheme.TAB_H && mx >= width - UiTheme.TAB_H) {
			onClose();
			return true;
		}
		if (my >= 0 && my < UiTheme.TAB_H && mx >= 0 && mx < UiTheme.TAB_W * Tab.values().length) {
			switchTab(Tab.values()[(int) (mx / UiTheme.TAB_W)]);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (modal != null) return modal.mouseReleased(event);
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		// Slider / 滚动条的拖动依赖这里：默认实现只分发给「聚焦」的控件，
		// 而 modal 内的控件不在 Screen 的 children 里，必须手动转发
		if (modal != null) return modal.mouseDragged(event, deltaX, deltaY);
		if (candidateList != null && candidateList.mouseDragged(event, deltaX, deltaY)) return true;
		if (ruleList != null && ruleList.mouseDragged(event, deltaX, deltaY)) return true;
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (modal != null) return modal.mouseScrolled(x, y, scrollX, scrollY);
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (modal != null) return modal.keyPressed(event);
		if (event.key() == InputConstants.KEY_ESCAPE && shouldCloseOnEsc()) {
			onClose();
			return true;
		}
		// 搜索框：↑↓ 在结果列表里移动；Tab 只补全；Enter 直接添加
		if (searchBox != null && searchBox.isFocused()) {
			int key = event.key();
			if (key == InputConstants.KEY_DOWN) {
				candidateList.moveSelection(1);
				return true;
			}
			if (key == InputConstants.KEY_UP) {
				candidateList.moveSelection(-1);
				return true;
			}
			if (key == InputConstants.KEY_TAB) {
				int index = candidateList.selected();
				if (index >= 0 && index < candidates.size()) {
					searchBox.setValue(candidates.get(index).id());
				}
				return true;
			}
			if (key == InputConstants.KEY_RETURN) {
				int index = candidateList.selected();
				if (index >= 0 && index < candidates.size()) {
					addRule(candidates.get(index).id());
				} else {
					addRule(null);
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (modal != null) return modal.charTyped(event);
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		GlowerConfig config = PreciseGlowerClient.config();
		if (config != null) config.lastTab = tab.name();
		PreciseGlowerClient.saveConfig();
		if (minecraft != null) McCompat.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// -------------------------------------------------------------------- host

	@Override
	public void requestFocus(AbstractWidget widget) {
		setFocused(widget);
	}

	@Override
	public int screenWidth() {
		return width;
	}

	@Override
	public int screenHeight() {
		return height;
	}

	@Override
	public Font font() {
		return font;
	}
}
