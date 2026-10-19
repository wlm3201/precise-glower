package com.preciseglower.client.gui.autocomplete;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.jetbrains.annotations.Nullable;

/**
 * NBT 自动补全（候选来自「世界里真实存在的实体 NBT」，而不是硬编码表）。
 * 支持：
 * <ul>
 *     <li>根层与嵌套层（{@code {Attributes:[{ }]}）的 key 补全</li>
 *     <li>基于样本实际取值的值补全（{@code {IsBaby: }}）</li>
 * </ul>
 */
public final class NbtSuggester {
	private static final Pattern PATH_KEY = Pattern.compile("([A-Za-z0-9_.-]+)\\s*:\\s*[\\[{]");
	/**
	 * 候选上限只是防止下拉过长（弹层本身可滚动）。
	 *
	 * <p>定得比样本里的 key 数还小是很要命的：实体根层轻轻松松就有几十个 key，
	 * 而 {@link CompoundTag} 内部是 HashMap、遍历顺序不定，于是被截掉的刚好可能是
	 * 这个类型独有的那几个（比如山羊的 {@code HasLeftHorn}）。</p>
	 */
	private static final int MAX_SUGGESTIONS = 64;

	private NbtSuggester() {
	}

	public static List<Suggestion> suggest(String input, int cursor, List<CompoundTag> samples) {
		if (input == null) return List.of();
		int end = Math.min(cursor, input.length());
		String prefix = input.substring(0, end);

		int lastOpen = prefix.lastIndexOf('{');
		if (lastOpen < 0) {
			if (prefix.isBlank()) return List.of(Suggestion.of("{", 0, prefix.length()));
			return List.of();
		}

		String inner = prefix.substring(lastOpen + 1);
		int lastComma = inner.lastIndexOf(',');
		String tail = inner.substring(lastComma + 1);
		int replaceFrom = end - tail.length();

		int colon = tail.indexOf(':');
		if (colon >= 0) {
			String key = tail.substring(0, colon).trim();
			String valuePrefix = tail.substring(colon + 1).trim();
			return suggestValues(key, valuePrefix, prefix.substring(0, lastOpen), samples, replaceFrom + colon + 1, end);
		}

		String keyPrefix = tail.trim();
		return suggestKeys(keyPrefix, prefix.substring(0, lastOpen), samples, replaceFrom, end);
	}

	// ------------------------------------------------------------------ keys

	private static List<Suggestion> suggestKeys(
		String keyPrefix, String outerPrefix, List<CompoundTag> samples, int replaceFrom, int replaceTo
	) {
		List<String> path = pathOf(outerPrefix);
		Set<String> keys = new LinkedHashSet<>();
		for (CompoundTag sample : samples) {
			CompoundTag node = drillDown(sample, path);
			if (node == null) continue;
			// 前缀过滤统一放在下面（要先把用户打出的开头引号剥掉再比）
			keys.addAll(node.keySet());
		}
		String prefix = normalizedPrefix(keyPrefix);
		List<Suggestion> out = new ArrayList<>();
		for (String key : keys) {
			if (out.size() >= MAX_SUGGESTIONS) break;
			if (!key.toLowerCase().startsWith(prefix)) continue;
			String hint = typeName(firstTagForKey(samples, path, key));
			out.add(new Suggestion(keyLiteral(key), replaceFrom, replaceTo, hint));
		}
		// 短的在前，同长度按字典序：样本顺序本身没有意义（CompoundTag 内部是 HashMap）
		out.sort(Comparator.comparingInt((Suggestion s) -> s.text().length()).thenComparing(Suggestion::text));
		return out;
	}

	// ---------------------------------------------------------------- values

	private static List<Suggestion> suggestValues(
		String key, String valuePrefix, String outerPrefix, List<CompoundTag> samples, int replaceFrom, int replaceTo
	) {
		List<String> path = pathOf(outerPrefix);
		// 原始值 -> 真正插进输入框的文本（字符串值可能要带引号）
		Map<String, String> values = new LinkedHashMap<>();
		for (CompoundTag sample : samples) {
			CompoundTag node = drillDown(sample, path);
			if (node == null || !node.contains(key)) continue;
			Tag tag = node.get(key);
			if (tag instanceof StringTag stringTag) {
				String raw = stringTag.value();
				values.put(raw, stringLiteral(raw));
				continue;
			}
			String literal = valueToString(tag);
			values.put(literal, literal);
			// 布尔标签存的是 0/1 的 byte，两种写法都给出来，让用户按习惯挑
			if (tag instanceof ByteTag byteTag && (byteTag.value() == 0 || byteTag.value() == 1)) {
				String bool = byteTag.value() == 0 ? "false" : "true";
				values.put(bool, bool);
			}
		}
		String prefix = normalizedPrefix(valuePrefix);
		List<Suggestion> out = new ArrayList<>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (out.size() >= MAX_SUGGESTIONS) break;
			if (!entry.getKey().toLowerCase().startsWith(prefix)) continue;
			out.add(Suggestion.of(entry.getValue(), replaceFrom, replaceTo));
		}
		out.sort((a, b) -> Integer.compare(a.text().length(), b.text().length()));
		return out;
	}

	// ---------------------------------------------------------------- helpers

	/** 从前缀中提取嵌套路径：{@code {Attributes:[} -> ["Attributes"] */
	private static List<String> pathOf(String outerPrefix) {
		List<String> path = new ArrayList<>();
		Matcher matcher = PATH_KEY.matcher(outerPrefix);
		while (matcher.find()) path.add(matcher.group(1));
		return path;
	}

	private static @Nullable CompoundTag drillDown(CompoundTag root, List<String> path) {
		CompoundTag current = root;
		for (String key : path) {
			if (current == null || !current.contains(key)) return null;
			Tag tag = current.get(key);
			if (tag instanceof CompoundTag compound) {
				current = compound;
			} else if (tag instanceof ListTag list && !list.isEmpty() && list.get(0) instanceof CompoundTag compound) {
				current = compound;
			} else {
				return null;
			}
		}
		return current;
	}

	private static @Nullable Tag firstTagForKey(List<CompoundTag> samples, List<String> path, String key) {
		for (CompoundTag sample : samples) {
			CompoundTag node = drillDown(sample, path);
			if (node != null && node.contains(key)) return node.get(key);
		}
		return null;
	}

	// ------------------------------------------------------------ SNBT 字面量

	/**
	 * 字符串值怎么插进输入框：能裸写就裸写，否则交给原版的 {@code StringTag#quoteAndEscape}。
	 *
	 * <p>判定不自己列字符集，而是拿原版解析器试一遍 —— 规则随版本在变（现在连数字开头都不让裸写了），
	 * 手抄一份迟早跟原版对不上。</p>
	 */
	private static String stringLiteral(String value) {
		return isBareAllowed("{k:" + value + "}", "k", tag -> tag instanceof StringTag) ? value : StringTag.quoteAndEscape(value);
	}

	/**
	 * key 同理：只有名字里带空格 / 冒号 / 引号之类、裸写会被原版解析器拒掉的才加引号。
	 * 绝大多数 key（variant、CustomName、NoAI……）都不该带。
	 */
	private static String keyLiteral(String key) {
		return isBareAllowed("{" + key + ":1b}", key, tag -> tag instanceof ByteTag) ? key : StringTag.quoteAndEscape(key);
	}

	/**
	 * @param snbt 用原版解析的一小段 compound
	 * @param lookupKey 解析完要取哪个键来校验（key 的判定里键名就是 key 本身，不能写死）
	 * @param wanted 解析结果必须是这个类型才算「能裸写」：{@code true} 会解析成 byte、
	 *               {@code 1.5} 会解析成 double，都不符合「我本来想给字符串」的意图
	 */
	private static boolean isBareAllowed(String snbt, String lookupKey, Predicate<Tag> wanted) {
		try {
			Tag parsed = TagParser.parseCompoundFully(snbt).get(lookupKey);
			return parsed != null && wanted.test(parsed);
		} catch (Exception e) {
			return false;
		}
	}

	/** 前缀比较前先剥掉空白与开头那个引号（用户可能已经自己打了半个引号） */
	private static String normalizedPrefix(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (!text.isEmpty() && (text.charAt(0) == '"' || text.charAt(0) == '\'')) text = text.substring(1);
		return text.toLowerCase();
	}

	private static String valueToString(@Nullable Tag tag) {
		if (tag == null) return "";
		if (tag instanceof NumericTag numeric) {
			double d = numeric.doubleValue();
			if (d == Math.rint(d) && Math.abs(d) < 1.0E10) return String.valueOf((long) d);
			return String.valueOf(d);
		}
		if (tag instanceof StringTag stringTag) return stringLiteral(stringTag.value());
		if (tag instanceof CompoundTag) return "{";
		if (tag instanceof ListTag) return "[";
		return tag.toString();
	}

	private static String typeName(@Nullable Tag tag) {
		if (tag == null) return "";
		if (tag instanceof CompoundTag) return "compound";
		if (tag instanceof ListTag) return "list";
		if (tag instanceof StringTag) return "string";
		if (tag instanceof NumericTag numeric) {
			double d = numeric.doubleValue();
			if (d == Math.rint(d) && Math.abs(d) < 1.0E10) return "number";
			return "float";
		}
		return "tag";
	}
}
