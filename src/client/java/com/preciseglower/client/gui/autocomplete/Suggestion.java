package com.preciseglower.client.gui.autocomplete;

import org.jetbrains.annotations.Nullable;

/** 一条补全候选。 */
public record Suggestion(String text, int replaceFrom, int replaceTo, @Nullable String hint) {
	public static Suggestion of(String text, int from, int to) {
		return new Suggestion(text, from, to, null);
	}
}
