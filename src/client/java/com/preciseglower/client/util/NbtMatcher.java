package com.preciseglower.client.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.jetbrains.annotations.Nullable;

public final class NbtMatcher {
    public static final NbtMatcher ANY = new NbtMatcher(null);

    private final @Nullable CompoundTag pattern;

    private NbtMatcher(@Nullable CompoundTag pattern) {
        this.pattern = pattern;
    }

    public static NbtMatcher parse(String input) throws CommandSyntaxException {
        if (input == null || input.isBlank()) return ANY;
        String s = input.trim();
        if (!s.startsWith("{")) s = "{" + s + "}";
        return new NbtMatcher(TagParser.parseCompoundFully(s));
    }

    public boolean isAny() {
        return pattern == null;
    }

    public String source() {
        return pattern == null ? "" : pattern.toString();
    }

    public boolean matches(CompoundTag actual) {
        if (pattern == null) return true;
        if (actual == null) return false;
        return matchesCompound(pattern, actual);
    }

    private static boolean matchesCompound(CompoundTag pattern, CompoundTag actual) {
        for (String key : pattern.keySet()) {
            if (!actual.contains(key)) return false;
            if (!matchesTag(pattern.get(key), actual.get(key))) return false;
        }
        return true;
    }

    private static boolean matchesTag(@Nullable Tag expected, @Nullable Tag actual) {
        if (expected == null) return true;
        if (actual == null) return false;
        if (expected instanceof CompoundTag expectedCompound) {
            return actual instanceof CompoundTag actualCompound
                    && matchesCompound(expectedCompound, actualCompound);
        }
        if (expected instanceof ListTag expectedList) {
            if (!(actual instanceof ListTag actualList) || actualList.size() < expectedList.size())
                return false;
            for (int i = 0; i < expectedList.size(); i++) {
                if (!matchesTag(expectedList.get(i), actualList.get(i))) return false;
            }
            return true;
        }
        if (expected instanceof NumericTag && actual instanceof NumericTag) {
            return ((NumericTag) expected).doubleValue() == ((NumericTag) actual).doubleValue();
        }
        if (expected instanceof StringTag expectedString
                && actual instanceof StringTag actualString) {
            return expectedString.value().equals(actualString.value());
        }
        if (expected instanceof ByteArrayTag
                || expected instanceof IntArrayTag
                || expected instanceof LongArrayTag) {
            return expected.equals(actual);
        }
        return expected.equals(actual);
    }
}
