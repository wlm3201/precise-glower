package com.preciseglower.client.gui.autocomplete;

import org.jetbrains.annotations.Nullable;

public record Suggestion(String text, int replaceFrom, int replaceTo, @Nullable String hint) {
    public static Suggestion of(String text, int from, int to) {
        return new Suggestion(text, from, to, null);
    }
}
