package com.preciseglower.client.match;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * 方块状态过滤器。
 * <ul>
 *     <li>{@code minecraft:furnace[lit=false]} —— 指定方块，走原版解析器（补全也复用原版）</li>
 *     <li>{@code #dirt} / {@code #dirt[snowy=true]} —— 方块标签，同样走原版解析器
 *         （{@code /execute if block} 那条分支），标签成员在解析时摊平成一个方块集合</li>
 *     <li>{@code *[waterlogged=true]} / {@code *} —— 任意方块 + 按「属性名=值」过滤，
 *         自己解析（原版解析器不认 {@code *}），补全由 {@link PropertyIndex} 提供</li>
 * </ul>
 * 未指定的属性不限；通配 / 标签模式下所有已指定属性都要匹配。
 */
public final class BlockStateFilter {
	private final @Nullable Block block;
	/**
	 * 标签规则命中的方块集合（{@code #dirt} 摊平后的成员）。
	 *
	 * <p>存的是 {@code Block} 实例而不是 {@code HolderSet}：方块是单例，跨注册表都是同一个对象，
	 * 于是绕开了「这个 Holder 属于哪个注册表」的问题 —— {@code Block#builtInRegistryHolder()}
	 * 属于内置注册表，而标签只绑在世界的注册表上，拿 holder 判成员会全部落空。</p>
	 */
	private final @Nullable Set<Block> tagBlocks;
	private final Map<Property<?>, Comparable<?>> properties;
	private final Map<String, String> loose;

	private BlockStateFilter(
		@Nullable Block block, @Nullable Set<Block> tagBlocks,
		Map<Property<?>, Comparable<?>> properties, Map<String, String> loose
	) {
		this.block = block;
		this.tagBlocks = tagBlocks;
		this.properties = properties;
		this.loose = loose;
	}

	/**
	 * 方块注册表：优先用当前世界的那一份。
	 *
	 * <p>内置注册表（{@link BuiltInRegistries#BLOCK}）在 bootstrap 时被 {@code bindAllTagsToEmpty()}
	 * 全部绑成了空标签；客户端的标签是进世界时服务器发下来的，最终落在
	 * {@code connection.registryAccess()} 里，而 {@code ClientLevel} 用的正是它
	 * （{@code BLOCK} 不在 {@code SYNCHRONIZED_REGISTRIES} 里，走的是「把标签绑回静态注册表」那条路，
	 * 所以内置注册表也会跟着有标签 —— 但那也要先进世界）。没进世界时两者都没有标签，
	 * 于是标签规则解析不了，界面上会提示「需进入世界后解析」。</p>
	 */
	public static HolderLookup<Block> lookup() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			Optional<Registry<Block>> registry = level.registryAccess().lookup(Registries.BLOCK);
			if (registry.isPresent()) return registry.get();
		}
		return BuiltInRegistries.BLOCK;
	}

	/**
	 * 当前能否拿到方块标签：直接看注册表里有没有标签，比「有没有世界」更准
	 * （从世界退回主菜单后内置注册表上还留着上一局的标签，那时仍然能解析）。
	 */
	public static boolean tagsAvailable() {
		return lookup().listTagIds().findAny().isPresent();
	}

	public static BlockStateFilter parse(String input) throws CommandSyntaxException {
		String s = input.trim();
		if (s.isEmpty()) throw new IllegalArgumentException(tr("precise-glower.error.empty"));
		int bracket = s.indexOf('[');
		String head = (bracket < 0 ? s : s.substring(0, bracket)).trim();
		String tail = bracket < 0 ? "" : s.substring(bracket).trim();

		if ("*".equals(head)) {
			return new BlockStateFilter(null, null, Map.of(), parseLoose(tail));
		}
		if (head.startsWith("#")) {
			return parseTag(s);
		}
		BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(lookup(), s, false);
		return new BlockStateFilter(result.blockState().getBlock(), null, result.properties(), Map.of());
	}

	/**
	 * 标签规则：{@code #dirt[snowy=true]}。
	 *
	 * <p>{@code parseForTesting} 是 {@code /execute if block} 用的那条分支（{@code forTesting=true}），
	 * 它认 {@code #} 开头；{@code /setblock} 那条（{@code parseForBlock}）遇到 {@code #} 直接报
	 * “tags disallowed”。标签的属性过滤是 {@code vagueProperties}（属性名 -&gt; 原始值，不校验
	 * 该标签里是否都有这个属性），和本类通配模式那套 {@link #loose} 是同一种语义。</p>
	 */
	private static BlockStateFilter parseTag(String s) throws CommandSyntaxException {
		Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parsed =
			BlockStateParser.parseForTesting(lookup(), new StringReader(s), false);
		Optional<BlockStateParser.TagResult> tag = parsed.right();
		if (tag.isEmpty()) throw new IllegalArgumentException(tr("precise-glower.error.parse"));

		Set<Block> members = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Holder<Block> holder : tag.get().tag()) members.add(holder.value());
		return new BlockStateFilter(null, members, Map.of(), tag.get().vagueProperties());
	}

	/** 解析 {@code [a=b,c=d]}（不校验属性是否存在，留给匹配阶段） */
	private static Map<String, String> parseLoose(String tail) {
		Map<String, String> out = new LinkedHashMap<>();
		String body = tail;
		if (body.startsWith("[")) body = body.substring(1);
		if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
		for (String part : body.split(",")) {
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
		}
		return out;
	}

	public @Nullable Block block() {
		return block;
	}

	public @Nullable Set<Block> tagBlocks() {
		return tagBlocks;
	}

	/** 这是一条标签规则（{@code #dirt}） */
	public boolean isTag() {
		return tagBlocks != null;
	}

	public boolean isAnyBlock() {
		return block == null && tagBlocks == null;
	}

	public boolean isAnyState() {
		return properties.isEmpty() && loose.isEmpty();
	}

	public boolean matches(BlockState state) {
		if (block != null && state.getBlock() != block) return false;
		if (tagBlocks != null && !tagBlocks.contains(state.getBlock())) return false;
		for (Map.Entry<Property<?>, Comparable<?>> entry : properties.entrySet()) {
			Property<?> property = entry.getKey();
			if (!state.hasProperty(property)) return false;
			if (!valueEquals(state, property, entry.getValue())) return false;
		}
		for (Map.Entry<String, String> entry : loose.entrySet()) {
			Property<?> property = findProperty(state, entry.getKey());
			if (property == null) return false;
			if (!looseEquals(state, property, entry.getValue())) return false;
		}
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static boolean valueEquals(BlockState state, Property<?> property, Comparable<?> expected) {
		Comparable actual = state.getValue((Property) property);
		return expected.equals(actual);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static boolean looseEquals(BlockState state, Property<?> property, String raw) {
		Optional<?> parsed = ((Property) property).getValue(raw);
		if (parsed.isEmpty()) return false;
		Comparable actual = state.getValue((Property) property);
		return parsed.get().equals(actual);
	}

	private static @Nullable Property<?> findProperty(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(name)) return property;
		}
		return null;
	}

	// ------------------------------------------------------------- 补全

	/**
	 * 指定方块 / 标签：复用原版补全（方块 id 或 {@code #tag} -&gt; 属性名 -&gt; 属性值）。
	 *
	 * <p>{@code forTesting=true} 是关键：它走的是 {@code /execute if block} 那条分支，
	 * 候选里才会带上 {@code #标签}（{@code /setblock} 那条不给）。</p>
	 */
	public static CompletableFuture<Suggestions> suggest(String input, int cursor) {
		SuggestionsBuilder builder = new SuggestionsBuilder(input, 0);
		try {
			return BlockStateParser.fillSuggestions(lookup(), builder, true, false);
		} catch (Exception e) {
			return builder.buildFuture();
		}
	}

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}
}
