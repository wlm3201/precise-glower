package com.preciseglower.client.config;

import com.preciseglower.client.util.NbtMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * 掉落物高亮规则：物品（可用 {@code *} 通配）+ 可选 NBT 过滤。
 * 例：{@code *{Count:64}} 高亮所有 64 个一组的掉落物。
 */
public class ItemRule extends Rule {
	public static final String WILDCARD = "*";

	public String item = "minecraft:diamond";
	/** NBT / 组件过滤表达式，可为空 */
	public String nbt = "";
	/** 数量下限：掉落物至少这么多才命中（1 = 不限） */
	public int minCount = 1;

	private transient @Nullable Item itemType;
	private transient NbtMatcher matcher = NbtMatcher.ANY;
	private transient String error;
	private transient boolean wildcard;

	@Override
	public String key() {
		return item + "|" + (nbt == null ? "" : nbt.trim());
	}

	@Override
	public String filterText() {
		return nbt == null ? "" : nbt.trim();
	}

	/** 归一化的数量下限（配置里写 0 / 负数时当作不限） */
	public int minCount() {
		return minCount < 1 ? 1 : minCount;
	}

	@Override
	public String filterSummary() {
		// 数量下限在列表里显示成「≥N」，NBT 那一栏留给隐藏规则
		return minCount() > 1 ? "≥" + minCount() : super.filterSummary();
	}

	@Override
	public int specificity() {
		// 带数量下限的比不带的更具体，和「带过滤表达式」同档
		return super.specificity() + (minCount() > 1 ? 1 : 0);
	}

	@Override
	public String error() {
		return error;
	}

	@Override
	public boolean isWildcard() {
		return wildcard;
	}

	public @Nullable Item itemType() {
		return itemType;
	}

	public NbtMatcher matcher() {
		return matcher;
	}

	@Override
	public void compile() {
		itemType = null;
		matcher = NbtMatcher.ANY;
		error = null;
		String t = item == null ? "" : item.trim();
		wildcard = WILDCARD.equals(t);
		if (!wildcard) {
			// 存在性按 keySet 判定（getValue 对不存在的 id 也返回非 null）
			Identifier parsed = Identifier.tryParse(t);
			if (parsed == null || !BuiltInRegistries.ITEM.keySet().contains(parsed)) {
				error = tr("precise-glower.error.unknown_item", t);
			} else {
				itemType = BuiltInRegistries.ITEM.getValue(parsed);
				if (itemType == null) error = tr("precise-glower.error.unknown_item", t);
			}
		}
		try {
			matcher = NbtMatcher.parse(nbt);
		} catch (Exception e) {
			matcher = NbtMatcher.ANY;
			// id 的错误优先展示，NBT 语法错误只在 id 没问题的时候覆盖
			if (error == null) error = tr("precise-glower.error.nbt");
		}
	}

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	@Override
	public String displayName() {
		String s = item == null ? "" : item.trim();
		if (WILDCARD.equals(s)) return WILDCARD;
		String localized = com.preciseglower.client.gui.autocomplete.NameIndex.itemName(s);
		if (!localized.isEmpty()) return localized;
		int i = s.indexOf(':');
		return i < 0 ? s : s.substring(i + 1);
	}

	@Override
	protected Rule newInstance() {
		return new ItemRule();
	}

	@Override
	protected void copyInto(Rule target) {
		ItemRule t = (ItemRule) target;
		t.item = this.item;
		t.nbt = this.nbt;
		t.minCount = this.minCount;
	}
}
