package com.preciseglower.client.gui.widget;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.BlockMode;
import com.preciseglower.client.config.BlockRule;
import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.GlowerConfig;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.gui.Graphics;
import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.gui.autocomplete.BrigadierSuggestions;
import com.preciseglower.client.gui.autocomplete.Candidate;
import com.preciseglower.client.gui.autocomplete.IdSuggester;
import com.preciseglower.client.gui.autocomplete.NbtSamples;
import com.preciseglower.client.gui.autocomplete.NbtSuggester;
import com.preciseglower.client.gui.autocomplete.Suggestion;
import com.preciseglower.client.match.BlockStateFilter;
import com.preciseglower.client.match.PropertyIndex;
import com.preciseglower.client.util.Colors;
import com.preciseglower.client.util.NbtMatcher;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑弹窗。
 * <ul>
 *     <li>ID 与「状态 / NBT」是两个独立输入框：方块状态框只写 {@code [lit=false]} 即可，
 *         解析与补全时会自动把 ID 拼在前面（所以不会陷入「必须带方块名」的限制）</li>
 *     <li>颜色：16 色预设 + HEX + R/G/B/不透明度四条滑块</li>
 *     <li>只有右上角 × / Cancel / Esc 能关闭（点击外部不会关闭，方便让输入框失焦收起候选）</li>
 * </ul>
 */
public final class RuleEditModal {
	public enum Kind { BLOCK, ENTITY, ITEM, HIDE_ENTITY, HIDE_ITEM }

	/** 隐藏规则没有颜色 / 线宽之类的外观参数，弹窗只保留 ID + NBT + 启用 */
	private boolean styled() {
		return kind == Kind.BLOCK || kind == Kind.ENTITY || kind == Kind.ITEM;
	}

	public interface Host {
		void requestFocus(AbstractWidget widget);

		int screenWidth();

		int screenHeight();

		Font font();
	}

	private static final int CLOSE_W = 16;

	private final Host host;
	private final Kind kind;
	private final Rule target;
	private final Consumer<Boolean> onClose;

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	private final List<AbstractWidget> children = new ArrayList<>();
	private final List<EditBox> editBoxes = new ArrayList<>();
	/** 正在拖动的控件（滑块拖出自己范围后也要继续跟着） */
	private AbstractWidget draggingChild;
	private SuggestionEditBox idBox;
	private SuggestionEditBox filterBox;
	private ColorPicker colorPicker;
	private Slider redSlider;
	private Slider greenSlider;
	private Slider blueSlider;
	private Slider alphaSlider;
	/** 隐藏实体的不透明度滑块（0 = 完全隐藏） */
	private Slider opacitySlider;
	private Slider widthSlider;
	private Slider rangeSlider;
	private FlatButton cancelButton;
	private FlatButton saveButton;

	private String statusText = "";
	private boolean statusOk = true;
	/** 语法正误提示行的 y（build() 里排出来，render() 画） */
	private int statusY;

	public RuleEditModal(Host host, Kind kind, Rule target, Consumer<Boolean> onClose) {
		this.host = host;
		this.kind = kind;
		this.target = target;
		this.onClose = onClose;

		this.width = UiTheme.MODAL_W;
		this.height = computeHeight();
		this.x = (host.screenWidth() - width) / 2;
		this.y = Math.max(6, (host.screenHeight() - height) / 2);

		build();
	}

	/**
	 * ID 之外还有一个过滤框：方块是状态、实体是 NBT、掉落物是「数量下限」。
	 * 隐藏物品只按 id 匹配，没有。
	 */
	private boolean hasFilterBox() {
		return kind != Kind.HIDE_ITEM;
	}

	/** 掉落物的过滤框填的是数量下限，不是 NBT */
	private boolean isCountBox() {
		return kind == Kind.ITEM;
	}

	private String countValue() {
		int min = target instanceof ItemRule itemRule ? itemRule.minCount() : 1;
		return min > 1 ? String.valueOf(min) : "";
	}

	/** 解析数量下限：空 = 不限；非法或小于 1 返回 empty */
	private static OptionalInt parseCount(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) return OptionalInt.of(1);
		try {
			int value = Integer.parseInt(text);
			return value >= 1 ? OptionalInt.of(value) : OptionalInt.empty();
		} catch (NumberFormatException e) {
			return OptionalInt.empty();
		}
	}

	/** 关联行（输入框 ↔ 它的正误提示）之间的紧凑间距 */
	private static final int TIGHT_GAP = 2;
	/** 分区之间（输入框组 ↔ 调色板 / 滑块组）的间距 */
	private static final int ROW_GAP = 4;
	/** 语法正误提示行的高度 */
	private static final int STATUS_H = 12;

	/**
	 * 弹窗总高。<b>必须与 {@link #build()} 里的 cy 推进逐行对应</b>——
	 * 那边改一步，这边就得改一步，否则取消 / 保存下面会多出一截空白。
	 */
	private int computeHeight() {
		int h = UiTheme.MODAL_HEAD_H + 4;                        // 标题栏 + 上边距
		h += UiTheme.CONTROL_H + TIGHT_GAP;                      // ID 框
		if (hasFilterBox()) h += UiTheme.CONTROL_H + TIGHT_GAP;  // 状态 / NBT / 数量框
		h += STATUS_H + ROW_GAP;                                 // 语法正误提示行
		if (styled()) {
			h += ColorPicker.preferredHeight() + ROW_GAP;        // 调色板 + 预览 + HEX
			h += 4 * UiTheme.SLIDER_ROW_H + ROW_GAP;             // R / G / B / A
			h += UiTheme.SLIDER_ROW_H + ROW_GAP;                 // 线宽
			h += UiTheme.SLIDER_ROW_H + ROW_GAP;                 // 范围
		} else if (kind == Kind.HIDE_ENTITY) {
			h += UiTheme.SLIDER_ROW_H + ROW_GAP;                 // 不透明度
		}
		h += (toggleCount() - 1) * UiTheme.TOGGLE_H;             // 倒数第二行的开关
		h += Math.max(UiTheme.TOGGLE_H, UiTheme.MODAL_BTN_H);    // 最后一行：启用 + 取消 / 保存
		return h + UiTheme.MODAL_PAD;
	}

	private int toggleCount() {
		if (styled()) {
			// 方块：线框 + 描边 + 启用；实体 / 掉落物：发光 + 包围盒 + 启用
			return 3;
		}
		// 隐藏实体：忽略交互 + 启用；隐藏物品只有启用
		return kind == Kind.HIDE_ENTITY ? 2 : 1;
	}

	// ------------------------------------------------------------------- build

	private void build() {
		Font font = host.font();
		int left = x + UiTheme.MODAL_PAD;
		int innerW = width - UiTheme.MODAL_PAD * 2;
		int cy = y + UiTheme.MODAL_HEAD_H + 4;

		idBox = new SuggestionEditBox(font, left, cy, innerW, UiTheme.CONTROL_H,
			Component.translatable("precise-glower.edit.id"), this::suggestId);
		idBox.setMaxLength(256);
		idBox.setValue(idValue());
		idBox.setHint(Component.translatable(kind == Kind.BLOCK
			? "precise-glower.hint.block_id"
			: kind == Kind.ENTITY || kind == Kind.HIDE_ENTITY
				? "precise-glower.hint.entity_id" : "precise-glower.hint.item_id"));
		idBox.setResponder(s -> {
			statusText = "";
			validate();
		});
		children.add(idBox);
		editBoxes.add(idBox);
		cy += UiTheme.CONTROL_H + TIGHT_GAP;

		if (hasFilterBox()) {
			String labelKey;
			String hintKey;
			if (isCountBox()) {
				labelKey = "precise-glower.edit.count";
				hintKey = "precise-glower.hint.count";
			} else if (kind == Kind.BLOCK) {
				labelKey = "precise-glower.edit.state";
				hintKey = "precise-glower.hint.state";
			} else {
				labelKey = "precise-glower.edit.nbt";
				hintKey = "precise-glower.hint.nbt";
			}
			Component filterLabel = Component.translatable(labelKey);
			// 数量下限是纯数字，不需要补全
			filterBox = new SuggestionEditBox(font, left, cy, innerW, UiTheme.CONTROL_H, filterLabel,
				isCountBox() ? null : this::suggestFilter);
			filterBox.setMaxLength(isCountBox() ? 8 : 512);
			filterBox.setValue(isCountBox() ? countValue() : target.filterText());
			filterBox.setHint(Component.translatable(hintKey));
			filterBox.setResponder(s -> {
				statusText = "";
				validate();
			});
			children.add(filterBox);
			editBoxes.add(filterBox);
			cy += UiTheme.CONTROL_H + TIGHT_GAP;
		}

		// 语法正误提示行：紧跟最后一个输入框（validate() 的结果画在这里）
		statusY = cy;
		cy += STATUS_H + ROW_GAP;

		if (!styled()) {
			if (kind == Kind.HIDE_ENTITY) {
				// 不透明度：0 = 完全不渲染，中间值按半透明渲染
				opacitySlider = new Slider(left, cy, innerW, Component.translatable("precise-glower.edit.opacity"),
					0, 100, target.opacity, "%.0f", v -> target.opacity = Math.round(v));
				children.add(opacitySlider);
				cy += UiTheme.SLIDER_ROW_H + ROW_GAP;
			}
		} else {
			colorPicker = new ColorPicker(left, cy, innerW, target.color, c -> {
				target.color = c;
				syncColorSliders();
			});
			children.add(colorPicker);
			editBoxes.add(colorPicker.hexBox());
			cy += colorPicker.getHeight() + ROW_GAP;

			int rgbLabelW = 18;
			redSlider = new Slider(left, cy, innerW, Component.literal("R"), 0, 255, Colors.red(target.color), "%.0f",
				v -> setChannel(0, v.intValue()), rgbLabelW, UiTheme.DANGER_SOLID, UiTheme.DANGER_SOLID);
			cy += UiTheme.SLIDER_ROW_H;
			greenSlider = new Slider(left, cy, innerW, Component.literal("G"), 0, 255, Colors.green(target.color), "%.0f",
				v -> setChannel(1, v.intValue()), rgbLabelW, UiTheme.CHANNEL_G, UiTheme.CHANNEL_G);
			cy += UiTheme.SLIDER_ROW_H;
			blueSlider = new Slider(left, cy, innerW, Component.literal("B"), 0, 255, Colors.blue(target.color), "%.0f",
				v -> setChannel(2, v.intValue()), rgbLabelW, UiTheme.CHANNEL_B, UiTheme.CHANNEL_B);
			cy += UiTheme.SLIDER_ROW_H;
			alphaSlider = new Slider(left, cy, innerW, Component.literal("A"), 0, 255,
				Colors.alpha(target.color), "%.0f", v -> {
					target.color = Colors.withAlpha(target.color, v.intValue());
					colorPicker.setColor(target.color);
				}, rgbLabelW, UiTheme.WHITE, UiTheme.WHITE);
			children.add(redSlider);
			children.add(greenSlider);
			children.add(blueSlider);
			children.add(alphaSlider);
			cy += UiTheme.SLIDER_ROW_H + ROW_GAP;

			widthSlider = new Slider(left, cy, innerW, Component.translatable("precise-glower.edit.width"),
				0.5F, 8.0F, target.lineWidth, "%.1f", v -> target.lineWidth = v);
			children.add(widthSlider);
			cy += UiTheme.SLIDER_ROW_H + ROW_GAP;

			rangeSlider = new Slider(left, cy, innerW, rangeLabel(),
				Rule.MIN_RANGE_CHUNKS, Rule.MAX_RANGE_CHUNKS, target.rangeChunks(), "%.0f",
				v -> target.setRangeChunks(Math.round(v)));
			children.add(rangeSlider);
			cy += UiTheme.SLIDER_ROW_H + ROW_GAP;

			if (kind == Kind.BLOCK) {
				// 线框 / 描边两个独立开关，可以只开一个也可以都开
				addToggle(left, cy, innerW, "precise-glower.edit.wireframe",
					target.blockMode().wireframe(),
					v -> target.blockMode = BlockMode.of(v, target.blockMode().outline()));
				cy += UiTheme.TOGGLE_H;
				addToggle(left, cy, innerW, "precise-glower.edit.outline",
					target.blockMode().outline(),
					v -> target.blockMode = BlockMode.of(target.blockMode().wireframe(), v));
				cy += UiTheme.TOGGLE_H;
			} else {
				// 实体 / 掉落物：描边（发光） + 线框（包围盒），两个开关的文案与方块侧统一
				addToggle(left, cy, innerW, "precise-glower.edit.outline", target.glow, v -> target.glow = v);
				cy += UiTheme.TOGGLE_H;
				addToggle(left, cy, innerW, "precise-glower.edit.wireframe", target.showBox, v -> target.showBox = v);
				cy += UiTheme.TOGGLE_H;
			}
		}

		if (kind == Kind.HIDE_ENTITY) {
			addToggle(left, cy, innerW, "precise-glower.edit.ignore_interaction",
				target.ignoreInteraction, v -> target.ignoreInteraction = v);
			cy += UiTheme.TOGGLE_H;
		}

		// 最后一个开关与取消 / 保存同一行：不再单独占一行，整体高度降下来
		int toggleRowY = cy;
		addToggle(left, toggleRowY, innerW, "precise-glower.edit.enabled", target.enabled, v -> target.enabled = v);
		int buttonY = toggleRowY + (UiTheme.TOGGLE_H - UiTheme.MODAL_BTN_H) / 2;
		cancelButton = new FlatButton(x + width - UiTheme.MODAL_PAD - 100, buttonY, 48, UiTheme.MODAL_BTN_H,
			Component.translatable("precise-glower.edit.cancel"), FlatButton.Style.NORMAL, b -> close());
		saveButton = new FlatButton(x + width - UiTheme.MODAL_PAD - 48, buttonY, 48, UiTheme.MODAL_BTN_H,
			Component.translatable("precise-glower.edit.save"), FlatButton.Style.PRIMARY, b -> save());
		children.add(cancelButton);
		children.add(saveButton);

		validate();
	}

	private String idValue() {
		return switch (kind) {
			case BLOCK -> ((BlockRule) target).blockId();
			case ENTITY, HIDE_ENTITY -> ((EntityRule) target).type;
			case ITEM, HIDE_ITEM -> ((ItemRule) target).item;
		};
	}

	private FlatToggle addToggle(int left, int y, int width, String key, boolean value, Consumer<Boolean> onChange) {
		FlatToggle toggle = new FlatToggle(left, y, width, Component.translatable(key), value, onChange);
		children.add(toggle);
		return toggle;
	}

	private void setChannel(int channel, int value) {
		int r = channel == 0 ? value : Colors.red(target.color);
		int g = channel == 1 ? value : Colors.green(target.color);
		int b = channel == 2 ? value : Colors.blue(target.color);
		target.color = Colors.argb(Colors.alpha(target.color), r, g, b);
		syncColorWidgets();
	}

	private void syncColorSliders() {
		if (redSlider == null) return;
		redSlider.setValue(Colors.red(target.color));
		greenSlider.setValue(Colors.green(target.color));
		blueSlider.setValue(Colors.blue(target.color));
		alphaSlider.setValue(Colors.alpha(target.color));
	}

	private void syncColorWidgets() {
		if (colorPicker == null) return;
		colorPicker.setColor(target.color);
		syncColorSliders();
	}

	// ------------------------------------------------------------- 补全

	private List<Suggestion> suggestId(String text, int cursor) {
		String prefix = text.substring(0, Math.min(cursor, text.length()));
		List<Candidate> candidates = switch (kind) {
			case BLOCK -> IdSuggester.blocks(prefix);
			case ENTITY, HIDE_ENTITY -> IdSuggester.entities(prefix);
			case ITEM, HIDE_ITEM -> IdSuggester.items(prefix);
		};
		List<Suggestion> out = new ArrayList<>();
		for (Candidate candidate : candidates) {
			out.add(new Suggestion(candidate.id(), 0, text.length(), candidate.label()));
		}
		return out;
	}

	private List<Suggestion> suggestFilter(String text, int cursor) {
		if (kind == Kind.BLOCK) {
			String id = idBox == null ? "" : idBox.getValue().trim();
			if (id.isEmpty()) id = "*";
			if ("*".equals(id)) return suggestWildcardState(text, cursor);
			// 原版补全需要完整表达式：把 ID 拼在前面，再把替换区间映射回状态框
			String full = id + text;
			int offset = id.length();
			List<Suggestion> out = new ArrayList<>();
			for (Suggestion suggestion : BrigadierSuggestions.blockState(full, offset + cursor)) {
				int from = Math.max(0, suggestion.replaceFrom() - offset);
				int to = Math.max(from, suggestion.replaceTo() - offset);
				out.add(new Suggestion(suggestion.text(), from, to, suggestion.hint()));
			}
			return out;
		}
		// NBT 候选必须跟着上面的 ID 输入框走：那边还没保存，target 里编译出来的类型可能还是旧的
		List<CompoundTag> samples = kind == Kind.ENTITY || kind == Kind.HIDE_ENTITY
			? NbtSamples.forEntity(entityTypeInEdit())
			: NbtSamples.forItem(itemInEdit());
		return NbtSuggester.suggest(text, cursor, samples);
	}

	/** ID 框里正在写的实体类型：通配符 / 查不到时退回 null（= 不限类型） */
	private @Nullable EntityType<?> entityTypeInEdit() {
		EntityType<?> parsed = IdSuggester.entity(idText());
		return parsed != null ? parsed : ((EntityRule) target).entityType();
	}

	/** ID 框里正在写的物品：同上，null 表示不限 */
	private @Nullable Item itemInEdit() {
		Item parsed = IdSuggester.item(idText());
		return parsed != null ? parsed : ((ItemRule) target).itemType();
	}

	/**
	 * 「范围区块」的标签：全局上限比滑条上限小时把实际能到几格标出来。
	 *
	 * <p>每规则范围还会和配置文件里的 {@code blockMaxDistance} / {@code entityMaxDistance}
	 * 取 min，不标出来的话就会出现「滑条拉满了却没变远」。</p>
	 */
	private Component rangeLabel() {
		GlowerConfig config = PreciseGlowerClient.config();
		String base = Component.translatable("precise-glower.edit.range").getString();
		int cap = config == null ? Rule.MAX_RANGE_CHUNKS
			: kind == Kind.BLOCK ? config.blockRangeCapChunks() : config.entityRangeCapChunks();
		return cap < Rule.MAX_RANGE_CHUNKS ? Component.literal(base + " ≤" + cap) : Component.literal(base);
	}

	/** ID 框的当前文本：通配符 {@code *} 视为「不限」 */
	private String idText() {
		String id = idBox == null ? "" : idBox.getValue().trim();
		return "*".equals(id) ? "" : id;
	}

	/** 通配方块（{@code *[waterlogged=true]}）的属性名 / 属性值补全 */
	private List<Suggestion> suggestWildcardState(String text, int cursor) {
		int end = Math.min(cursor, text.length());
		String prefix = text.substring(0, end);
		int open = prefix.lastIndexOf('[');
		if (open < 0) {
			if (prefix.trim().isEmpty()) return List.of(new Suggestion("[", 0, prefix.length(), null));
			return List.of();
		}
		String after = prefix.substring(open + 1);
		int eq = after.lastIndexOf('=');
		int comma = after.lastIndexOf(',');
		if (eq > comma) {
			String name = after.substring(comma + 1, eq).trim();
			String valuePrefix = after.substring(eq + 1);
			int from = open + 1 + eq + 1;
			List<Suggestion> out = new ArrayList<>();
			for (String value : PropertyIndex.values(name)) {
				if (!value.toLowerCase().startsWith(valuePrefix.toLowerCase())) continue;
				out.add(new Suggestion(value, from, end, null));
				if (out.size() >= 24) break;
			}
			return out;
		}
		int from = open + 1 + comma + 1;
		String namePrefix = after.substring(Math.max(0, comma + 1));
		List<Suggestion> out = new ArrayList<>();
		for (String name : PropertyIndex.names()) {
			if (!name.toLowerCase().startsWith(namePrefix.toLowerCase())) continue;
			out.add(new Suggestion(name, from, end, null));
			if (out.size() >= 24) break;
		}
		return out;
	}

	// ------------------------------------------------------------- 校验/保存

	private String filterValue() {
		String raw = filterBox == null ? "" : filterBox.getValue().trim();
		if (raw.isEmpty()) return "";
		if (kind == Kind.BLOCK && !raw.startsWith("[")) return "[" + raw + "]";
		return raw;
	}

	/** id 不存在时的文案要带上 id 本身，光说「未知 ID」不好定位 */
	private String unknownKey() {
		return switch (kind) {
			case BLOCK -> "precise-glower.error.unknown_block";
			case ENTITY, HIDE_ENTITY -> "precise-glower.error.unknown_entity";
			case ITEM, HIDE_ITEM -> "precise-glower.error.unknown_item";
		};
	}

	private void validate() {
		String id = idBox == null ? "" : idBox.getValue().trim();
		if (id.isEmpty()) {
			statusOk = false;
			statusText = Component.translatable("precise-glower.status.empty_id").getString();
			return;
		}
		try {
			if (kind == Kind.BLOCK) {
				String blockId = id.contains("[") ? id.substring(0, id.indexOf('[')).trim() : id;
				if (!"*".equals(blockId) && !blockId.startsWith("#") && IdSuggester.block(blockId) == null) {
					statusOk = false;
					statusText = Component.translatable(unknownKey(), blockId).getString();
					return;
				}
				// 标签（#dirt）不存在时给专门的文案；没进世界时标签暂时查不到，也要说清楚
				if (blockId.startsWith("#")) {
					if (!BlockStateFilter.tagsAvailable()) {
						statusOk = false;
						statusText = Component.translatable("precise-glower.error.tag_needs_world", blockId).getString();
						return;
					}
					if (!IdSuggester.blockTagExists(blockId)) {
						statusOk = false;
						statusText = Component.translatable("precise-glower.error.unknown_tag", blockId).getString();
						return;
					}
				}
				BlockStateFilter.parse(id + filterValue());
				// 状态为空 = 匹配该方块的全部状态，属于合法输入，不显示状态行
				if (filterValue().isEmpty()) {
					statusOk = true;
					statusText = "";
					return;
				}
			} else {
				if (!"*".equals(id)) {
					boolean known = kind == Kind.ENTITY || kind == Kind.HIDE_ENTITY
						? IdSuggester.entity(id.trim()) != null
						: IdSuggester.item(id.trim()) != null;
					if (!known) {
						statusOk = false;
						statusText = Component.translatable(unknownKey(), id.trim()).getString();
						return;
					}
				}
				if (isCountBox()) {
					String raw = filterBox == null ? "" : filterBox.getValue().trim();
					if (parseCount(raw).isEmpty()) {
						statusOk = false;
						statusText = Component.translatable("precise-glower.error.count").getString();
						return;
					}
				} else {
					String nbt = filterBox == null ? "" : filterBox.getValue().trim();
					if (!nbt.isEmpty()) NbtMatcher.parse(nbt);
				}
				if (filterBox == null || filterBox.getValue().trim().isEmpty()) {
					statusOk = true;
					statusText = "";
					return;
				}
			}
			statusOk = true;
			statusText = Component.translatable("precise-glower.status.ok").getString();
		} catch (Exception e) {
			statusOk = false;
			statusText = Component.translatable("precise-glower.error.parse").getString();
		}
	}

	private void save() {
		if (colorPicker != null) colorPicker.syncFromHexBox();
		validate();
		if (!statusOk) return;
		String id = idBox == null ? "" : idBox.getValue().trim();
		// 过滤框的内容：方块是状态、实体是 NBT、掉落物是数量下限
		String filter = filterBox == null ? "" : filterBox.getValue().trim();
		switch (kind) {
			case BLOCK -> ((BlockRule) target).id = id + filterValue();
			case ENTITY, HIDE_ENTITY -> {
				((EntityRule) target).type = id;
				((EntityRule) target).nbt = filter;
			}
			case ITEM -> {
				((ItemRule) target).item = id;
				((ItemRule) target).minCount = parseCount(filter).orElse(1);
			}
			case HIDE_ITEM -> {
				((ItemRule) target).item = id;
				((ItemRule) target).nbt = filter;
			}
		}
		if (onClose != null) onClose.accept(true);
	}

	private void close() {
		if (onClose != null) onClose.accept(false);
	}

	// ------------------------------------------------------------------ render

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = host.font();
		// 弹窗底下压着主界面的列表，必须自己铺一层底盖住它们
		graphics.fill(x, y, x + width, y + height, UiTheme.MASK);
		graphics.fill(x, y, x + width, y + UiTheme.MODAL_HEAD_H, UiTheme.MASK);
		graphics.fill(x, y + UiTheme.MODAL_HEAD_H, x + width, y + UiTheme.MODAL_HEAD_H + 1, UiTheme.LINE);
		// 边框最后画，才能包住标题栏
		graphics.outline(x, y, width, height, UiTheme.LINE);
		String title = Component.translatable("precise-glower.edit.title").getString() + " · " + target.displayName();
		graphics.text(font, font.plainSubstrByWidth(title, width - CLOSE_W - 10), x + 6, y + 5, UiTheme.WHITE, false);

		boolean closeHover = isOverClose(mouseX, mouseY);
		// 与「删除条目」区分开：关闭按钮悬停只描红边，不填充红；常态不叠底
		graphics.outline(x + width - CLOSE_W, y, CLOSE_W, UiTheme.MODAL_HEAD_H, closeHover ? UiTheme.DANGER_SOLID : UiTheme.LINE);
		Graphics.drawX(graphics, x + width - 12, y + 4, 8, closeHover ? UiTheme.DANGER_SOLID : UiTheme.WHITE);

		if (!statusText.isEmpty()) {
			graphics.text(font, statusText, x + UiTheme.MODAL_PAD, statusY, statusOk ? UiTheme.WHITE : UiTheme.WARN, false);
		}

		for (AbstractWidget child : children) {
			// 这些控件不是 screen 的 child，没人维护 isHovered：控件内部自己按鼠标位置判悬停
			child.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}
		for (EditBox box : editBoxes) {
			if (box instanceof SuggestionEditBox suggestionBox && suggestionBox.isPopupOpen()) {
				graphics.nextStratum();
				suggestionBox.renderPopup(graphics, font, mouseX, mouseY);
			}
		}
	}

	private boolean isOverClose(double mouseX, double mouseY) {
		return mouseX >= x + width - CLOSE_W && mouseX <= x + width && mouseY >= y && mouseY <= y + UiTheme.MODAL_HEAD_H;
	}

	public void tick() {
	}

	// ------------------------------------------------------------------- input

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (isOverClose(mx, my)) {
			close();
			return true;
		}
		// 点击弹窗外部：什么都不做（不再自动关闭），只消费事件
		if (mx < x || mx >= x + width || my < y || my >= y + height) return true;

		for (EditBox box : editBoxes) {
			if (box instanceof SuggestionEditBox suggestionBox && suggestionBox.isPopupOpen()
				&& suggestionBox.popupMouseClicked(mx, my)) {
				return true;
			}
		}

		if (colorPicker != null && colorPicker.isMouseOver(mx, my)) {
			colorPicker.onClick(event, doubleClick);
			if (colorPicker.hexBox().isFocused()) setFocus(colorPicker.hexBox());
			return true;
		}

		for (AbstractWidget child : children) {
			if (child == colorPicker || !child.isMouseOver(mx, my)) continue;
			if (child instanceof EditBox box) {
				setFocus(box);
				box.onClick(event, doubleClick);
				if (child instanceof SuggestionEditBox suggestionBox) suggestionBox.refreshSuggestions();
				return true;
			}
			// 滑块要能在拖出自己范围后继续响应，记下来交给 mouseDragged
			draggingChild = child;
			child.mouseClicked(event, doubleClick);
			return true;
		}
		setFocus(null);
		return true;
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingChild != null) {
			draggingChild.mouseReleased(event);
			draggingChild = null;
			return true;
		}
		for (AbstractWidget child : children) {
			if (child.isMouseOver(event.x(), event.y())) {
				child.mouseReleased(event);
				return true;
			}
		}
		return false;
	}

	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingChild != null) return draggingChild.mouseDragged(event, dx, dy);
		// 补全弹层在输入框下方，拖动它的滚动条时鼠标并不在输入框上
		for (EditBox box : editBoxes) {
			if (box instanceof SuggestionEditBox suggestionBox && suggestionBox.isDraggingPopupBar()) {
				return suggestionBox.mouseDragged(event, dx, dy);
			}
		}
		for (AbstractWidget child : children) {
			if (child.isMouseOver(event.x(), event.y()) && child.mouseDragged(event, dx, dy)) return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		// 补全弹层悬在输入框外面，滚轮要先问它
		for (EditBox box : editBoxes) {
			if (box instanceof SuggestionEditBox suggestionBox
				&& suggestionBox.popupMouseScrolled(mx, my, scrollY)) return true;
		}
		for (AbstractWidget child : children) {
			if (child.isMouseOver(mx, my) && child.mouseScrolled(mx, my, scrollX, scrollY)) return true;
		}
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		for (EditBox box : editBoxes) {
			if (box.isFocused()) {
				boolean handled = box instanceof SuggestionEditBox suggestionBox
					? suggestionBox.keyPressed(event)
					: box.keyPressed(event);
				if (handled) return true;
			}
		}
		if (event.key() == InputConstants.KEY_ESCAPE) {
			close();
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN) {
			save();
			return true;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		for (EditBox box : editBoxes) {
			if (box.isFocused()) {
				boolean handled = box.charTyped(event);
				if (colorPicker != null && box == colorPicker.hexBox()) colorPicker.syncFromHexBox();
				return handled;
			}
		}
		return false;
	}

	private void setFocus(EditBox widget) {
		for (EditBox box : editBoxes) box.setFocused(box == widget);
		if (host != null) host.requestFocus(widget);
	}

	public void init(Font font) {
		setFocus(idBox);
	}
}
