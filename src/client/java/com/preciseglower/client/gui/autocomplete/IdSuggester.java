package com.preciseglower.client.gui.autocomplete;

import com.preciseglower.client.match.BlockStateFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 方块 / 实体 / 物品搜索：既匹配注册表 id，也匹配本地化名（可以直接搜中文）。
 * 结果里始终保留通配符 {@code *} 条目。
 */
public final class IdSuggester {
	private static final int MAX = 40;
	public static final String WILDCARD = "*";

	private IdSuggester() {
	}

	public static List<Candidate> blocks(String prefix) {
		String raw = prefix == null ? "" : prefix.trim();
		// 打 # 就是在找方块标签（#dirt），此时不给普通方块 id，免得混在一起看不清
		if (raw.startsWith("#")) return matchTags(raw.substring(1));
		return match(prefix, vanillaIds(BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::toString).toList()), NameIndex::blockName);
	}

	/** 方块标签搜索：候选自带 {@code #} 前缀，右侧标「标签」 */
	private static List<Candidate> matchTags(String namePrefix) {
		String p = namePrefix.toLowerCase();
		String label = Component.translatable("precise-glower.tag").getString();
		List<Candidate> prefixHits = new ArrayList<>();
		List<Candidate> containsHits = new ArrayList<>();
		for (String tag : blockTags()) {
			String name = tag.substring(1);
			Candidate candidate = Candidate.of(tag, label);
			if (name.toLowerCase().startsWith(p)) prefixHits.add(candidate);
			else if (name.toLowerCase().contains(p)) containsHits.add(candidate);
		}
		Comparator<Candidate> byLength = Comparator.comparingInt(c -> c.id().length());
		prefixHits.sort(byLength);
		containsHits.sort(byLength);
		List<Candidate> out = new ArrayList<>();
		for (List<Candidate> group : List.of(prefixHits, containsHits)) {
			for (Candidate candidate : group) {
				if (out.size() >= MAX) break;
				out.add(candidate);
			}
		}
		return out;
	}

	/**
	 * 方块标签 id（带 {@code #}），取自当前世界的注册表。
	 *
	 * <p>没进世界时拿不到标签（内置注册表的标签被绑成空的），返回空列表 —— 于是标签候选为空，
	 * 添加标签规则也会被挡住并提示。</p>
	 */
	public static List<String> blockTags() {
		HolderLookup<Block> lookup = BlockStateFilter.lookup();
		if (lookup == null) return List.of();
		return lookup.listTagIds()
			.map(tag -> "#" + tag.location())
			.filter(id -> id.startsWith("#minecraft:"))
			.sorted()
			.toList();
	}

	public static List<Candidate> entities(String prefix) {
		return match(prefix, vanillaIds(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Identifier::toString).toList()), NameIndex::entityName);
	}

	public static List<Candidate> items(String prefix) {
		return match(prefix, vanillaIds(BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString).toList()), NameIndex::itemName);
	}

	private static List<String> vanillaIds(List<String> all) {
		return all.stream().filter(id -> id.startsWith("minecraft:")).sorted().toList();
	}

	/**
	 * 存在性按各自的注册表判定。
	 *
	 * <p>以前是「三个注册表有一个命中就算通过」，于是在实体页输入一个物品 id 也能加成功，
	 * 保存后才会炸出一条看不懂的报错。现在各管各的。</p>
	 */
	public static boolean blockExists(String id) {
		String trimmed = id == null ? "" : id.trim();
		if (trimmed.startsWith("#")) return blockTagExists(trimmed);
		return inRegistry(trimmed, BuiltInRegistries.BLOCK);
	}

	/** 方块标签是否存在（{@code #dirt} / {@code #minecraft:dirt}） */
	public static boolean blockTagExists(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (!name.startsWith("#")) return false;
		Identifier parsed = Identifier.tryParse(name.substring(1));
		if (parsed == null) return false;
		HolderLookup<Block> lookup = BlockStateFilter.lookup();
		return lookup != null && lookup.get(TagKey.create(Registries.BLOCK, parsed)).isPresent();
	}

	public static boolean entityExists(String id) {
		return inRegistry(id, BuiltInRegistries.ENTITY_TYPE);
	}

	public static boolean itemExists(String id) {
		return inRegistry(id, BuiltInRegistries.ITEM);
	}

	/**
	 * 存在性只看 keySet。
	 *
	 * <p>不能用 {@code registry.getValue(id) != null} 判定：这个版本里它对不存在的 id
	 * 也返回非 null（于是 {@code asdf} 会被当成合法 id 加进来，方块侧则一路走到
	 * 解析器抛异常变成「无法解析」）。只有 {@code Identifier.tryParse} 失败
	 * （比如输入里带空格）才会被拦住，这就是之前三种类型表现不一致的原因。</p>
	 */
	private static boolean inRegistry(String id, Registry<?> registry) {
		String trimmed = id == null ? "" : id.trim();
		if (WILDCARD.equals(trimmed)) return true;
		Identifier parsed = Identifier.tryParse(trimmed);
		return parsed != null && registry.keySet().contains(parsed);
	}

	/** 查表：先确认 id 在注册表里，再取值，避免拿到不存在的条目 */
	private static <T> T lookup(Registry<T> registry, String id) {
		Identifier parsed = Identifier.tryParse(id == null ? "" : id.trim());
		return parsed == null || !registry.keySet().contains(parsed) ? null : registry.getValue(parsed);
	}

	public static Block block(String id) {
		return lookup(BuiltInRegistries.BLOCK, id);
	}

	public static EntityType<?> entity(String id) {
		return lookup(BuiltInRegistries.ENTITY_TYPE, id);
	}

	public static Item item(String id) {
		return lookup(BuiltInRegistries.ITEM, id);
	}

	/** id 前缀 > 本地名前缀 > 本地名包含 > id 包含 */
	private static List<Candidate> match(String prefix, List<String> pool, Function<String, String> nameOf) {
		String raw = prefix == null ? "" : prefix.trim();
		String p = raw.toLowerCase();
		List<Candidate> out = new ArrayList<>();

		if (p.isEmpty() || WILDCARD.equals(raw)) {
			out.add(Candidate.of(WILDCARD, Component.translatable("precise-glower.all").getString()));
		}

		List<Candidate> idPrefix = new ArrayList<>();
		List<Candidate> namePrefix = new ArrayList<>();
		List<Candidate> nameContains = new ArrayList<>();
		List<Candidate> idContains = new ArrayList<>();

		if (!p.isEmpty() && !WILDCARD.equals(raw)) {
			for (String id : pool) {
				String lower = id.toLowerCase();
				String name = nameOf.apply(id);
				String lowerName = name.toLowerCase();
				Candidate candidate = Candidate.of(id, name);
				if (lower.startsWith(p)) idPrefix.add(candidate);
				else if (!lowerName.isEmpty() && lowerName.startsWith(p)) namePrefix.add(candidate);
				else if (!lowerName.isEmpty() && lowerName.contains(p)) nameContains.add(candidate);
				else if (lower.contains(p)) idContains.add(candidate);
			}
			Comparator<Candidate> byLength = Comparator.comparingInt(c -> c.id().length());
			idPrefix.sort(byLength);
			namePrefix.sort(byLength);
			nameContains.sort(byLength);
			idContains.sort(byLength);
		}

		for (List<Candidate> group : List.of(idPrefix, namePrefix, nameContains, idContains)) {
			for (Candidate candidate : group) {
				if (out.size() >= MAX) break;
				out.add(candidate);
			}
		}
		return out;
	}
}
