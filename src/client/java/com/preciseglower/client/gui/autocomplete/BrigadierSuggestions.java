package com.preciseglower.client.gui.autocomplete;

import com.preciseglower.client.match.BlockStateFilter;
import java.util.ArrayList;
import java.util.List;

/** brigadier 建议 -> 我们自己的 {@link Suggestion} 列表。 */
public final class BrigadierSuggestions {
	private BrigadierSuggestions() {
	}

	/** 复用原版 {@code /setblock} 的方块状态补全 */
	public static List<Suggestion> blockState(String text, int cursor) {
		try {
			com.mojang.brigadier.suggestion.Suggestions result = BlockStateFilter.suggest(text, cursor).join();
			int start = result.getRange().getStart();
			List<Suggestion> out = new ArrayList<>();
			for (com.mojang.brigadier.suggestion.Suggestion suggestion : result.getList()) {
				out.add(Suggestion.of(suggestion.getText(), Math.min(start, cursor), cursor));
			}
			return out;
		} catch (Exception e) {
			return List.of();
		}
	}
}
