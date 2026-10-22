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

public final class BlockStateFilter {
    private final @Nullable Block block;

    private final @Nullable Set<Block> tagBlocks;

    private final Map<Property<?>, Comparable<?>> properties;
    private final Map<String, String> loose;

    private BlockStateFilter(
            @Nullable Block block,
            @Nullable Set<Block> tagBlocks,
            Map<Property<?>, Comparable<?>> properties,
            Map<String, String> loose) {
        this.block = block;
        this.tagBlocks = tagBlocks;
        this.properties = properties;
        this.loose = loose;
    }

    public static HolderLookup<Block> lookup() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Optional<Registry<Block>> registry = level.registryAccess().lookup(Registries.BLOCK);
            if (registry.isPresent()) return registry.get();
        }
        return BuiltInRegistries.BLOCK;
    }

    public static boolean tagsAvailable() {
        return lookup().listTagIds().findAny().isPresent();
    }

    public static BlockStateFilter parse(String input) throws CommandSyntaxException {
        String s = input.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(tr("preciseglower.error.empty"));
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
        return new BlockStateFilter(
                result.blockState().getBlock(), null, result.properties(), Map.of());
    }

    private static BlockStateFilter parseTag(String s) throws CommandSyntaxException {
        Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parsed =
                BlockStateParser.parseForTesting(lookup(), new StringReader(s), false);
        Optional<BlockStateParser.TagResult> tag = parsed.right();
        if (tag.isEmpty()) throw new IllegalArgumentException(tr("preciseglower.error.parse"));

        Set<Block> members = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Holder<Block> holder : tag.get().tag()) members.add(holder.value());
        return new BlockStateFilter(null, members, Map.of(), tag.get().vagueProperties());
    }

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean valueEquals(
            BlockState state, Property<?> property, Comparable<?> expected) {
        Comparable actual = state.getValue((Property) property);
        return expected.equals(actual);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
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
