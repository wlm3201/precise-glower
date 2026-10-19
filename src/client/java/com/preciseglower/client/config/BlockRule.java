package com.preciseglower.client.config;

import com.preciseglower.client.gui.autocomplete.IdSuggester;
import com.preciseglower.client.match.BlockStateFilter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 方块高亮规则。
 * <ul>
 *     <li>{@code minecraft:furnace[lit=false]} —— 指定方块 + 指定状态</li>
 *     <li>{@code #dirt} —— 方块标签（泥土 / 砂土 / 灰化土……），{@code #dirt[snowy=true]} 再叠状态</li>
 *     <li>{@code *} —— 所有方块</li>
 *     <li>{@code *[waterlogged=true]} —— 所有「含水」方块（按状态过滤，不限方块种类）</li>
 * </ul>
 */
public class BlockRule extends Rule {
	public static final String WILDCARD = "*";

	/** 完整表达式，如 {@code minecraft:furnace[lit=false]} 或 {@code *[waterlogged=true]} */
	public String id = "minecraft:chest";

	private transient @Nullable BlockStateFilter filter;
	private transient String error;
	private transient Block block;

	@Override
	public String key() {
		return id;
	}

	/** 过滤表达式：只含状态部分，如 {@code [lit=false]} */
	@Override
	public String filterText() {
		int i = id.indexOf('[');
		return i < 0 ? "" : id.substring(i);
	}

	/** 方块 id 部分（不含状态） */
	public String blockId() {
		int i = id.indexOf('[');
		return i < 0 ? id.trim() : id.substring(0, i).trim();
	}

	@Override
	public boolean isWildcard() {
		return WILDCARD.equals(blockId());
	}

	/** 这是一条标签规则（{@code #dirt} / {@code #dirt[snowy=true]}） */
	public boolean isTag() {
		return blockId().startsWith("#");
	}

	/**
	 * 标签介于通配符与具体 id 之间：{@code *} &lt; {@code #tag} &lt; {@code id}，带状态再各 +1。
	 *
	 * <p>它只是「新规则落在哪一档」的参考（见 {@link Rule#specificity()}），真正的优先级是列表下标。</p>
	 */
	@Override
	public int specificity() {
		if (isWildcard()) return super.specificity();
		if (isTag()) return 1 + (filterText().isBlank() ? 0 : 1);
		return super.specificity();
	}

	@Override
	public String error() {
		return error;
	}

	public @Nullable Block block() {
		return block;
	}

	public @Nullable BlockStateFilter filter() {
		return filter;
	}

	@Override
	public void compile() {
		filter = null;
		block = null;
		error = null;
		if (id == null || id.isBlank()) {
			error = tr("precise-glower.error.empty");
			return;
		}
		String rawId = blockId();
		if (isTag()) {
			// 标签来自服务器下发的数据，没进过世界时注册表里一个标签都没有
			if (!BlockStateFilter.tagsAvailable()) {
				error = tr("precise-glower.error.tag_needs_world", rawId);
				return;
			}
			if (!IdSuggester.blockTagExists(rawId)) {
				error = tr("precise-glower.error.unknown_tag", rawId);
				return;
			}
		} else if (!WILDCARD.equals(rawId)) {
			Identifier parsedId = Identifier.tryParse(rawId);
			// 存在性按 keySet 判定（getValue 对不存在的 id 也返回非 null）
			if (parsedId == null || !BuiltInRegistries.BLOCK.keySet().contains(parsedId)) {
				// 先自查一遍 id：原版解析器那句「未知的方块类型"xxx" at position…」太长，不适合贴在列表里
				error = tr("precise-glower.error.unknown_block", rawId);
				return;
			}
		}
		try {
			filter = BlockStateFilter.parse(id);
			if (filter.isAnyBlock() && filter.isAnyState()) {
				// 裸 * 会匹配整个世界，直接拒绝
				filter = null;
				error = tr("precise-glower.error.wildcard_state");
				return;
			}
			block = filter.block();
		} catch (Exception e) {
			error = tr("precise-glower.error.parse");
		}
	}

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** 显示名：优先本地化名（中文），通配符显示 {@code *}，标签保留 {@code #} 只省掉命名空间 */
	@Override
	public String displayName() {
		String s = blockId();
		if (WILDCARD.equals(s)) return WILDCARD;
		if (isTag()) {
			// 标签没有本地化名，且不能把 # 吃掉 —— 否则看起来就是一个普通方块
			int i = s.indexOf(':');
			return "#" + (i < 0 ? s.substring(1) : s.substring(i + 1));
		}
		String localized = com.preciseglower.client.gui.autocomplete.NameIndex.blockName(s);
		if (!localized.isEmpty()) return localized;
		int i = s.indexOf(':');
		return i < 0 ? s : s.substring(i + 1);
	}

	public String namespace() {
		String s = blockId();
		int i = s.indexOf(':');
		return i < 0 ? "minecraft" : s.substring(0, i);
	}

	public boolean isVanilla() {
		return "minecraft".equals(namespace());
	}

	public String registryName() {
		return BuiltInRegistries.BLOCK.getKey(block()) == null ? blockId() : String.valueOf(BuiltInRegistries.BLOCK.getKey(block()));
	}

	@Override
	protected Rule newInstance() {
		return new BlockRule();
	}

	@Override
	protected void copyInto(Rule target) {
		((BlockRule) target).id = this.id;
	}
}
