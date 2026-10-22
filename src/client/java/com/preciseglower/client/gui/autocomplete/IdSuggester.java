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

public final class IdSuggester {
    private static final int MAX = 40;
    public static final String WILDCARD = "*";

    private IdSuggester() {}

    public static List<Candidate> blocks(String prefix) {
        String raw = prefix == null ? "" : prefix.trim();
        if (raw.startsWith("#")) return matchTags(raw.substring(1));
        return match(
                prefix,
                vanillaIds(
                        BuiltInRegistries.BLOCK.keySet().stream()
                                .map(Identifier::toString)
                                .toList()),
                NameIndex::blockName);
    }

    private static List<Candidate> matchTags(String namePrefix) {
        String p = namePrefix.toLowerCase();
        String label = Component.translatable("preciseglower.tag").getString();
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
        return match(
                prefix,
                vanillaIds(
                        BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                                .map(Identifier::toString)
                                .toList()),
                NameIndex::entityName);
    }

    public static List<Candidate> items(String prefix) {
        return match(
                prefix,
                vanillaIds(
                        BuiltInRegistries.ITEM.keySet().stream()
                                .map(Identifier::toString)
                                .toList()),
                NameIndex::itemName);
    }

    private static List<String> vanillaIds(List<String> all) {
        return all.stream().filter(id -> id.startsWith("minecraft:")).sorted().toList();
    }

    public static boolean blockExists(String id) {
        String trimmed = id == null ? "" : id.trim();
        if (trimmed.startsWith("#")) return blockTagExists(trimmed);
        return inRegistry(trimmed, BuiltInRegistries.BLOCK);
    }

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

    private static boolean inRegistry(String id, Registry<?> registry) {
        String trimmed = id == null ? "" : id.trim();
        if (WILDCARD.equals(trimmed)) return true;
        Identifier parsed = Identifier.tryParse(trimmed);
        return parsed != null && registry.keySet().contains(parsed);
    }

    private static <T> T lookup(Registry<T> registry, String id) {
        Identifier parsed = Identifier.tryParse(id == null ? "" : id.trim());
        return parsed == null || !registry.keySet().contains(parsed)
                ? null
                : registry.getValue(parsed);
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

    private static List<Candidate> match(
            String prefix, List<String> pool, Function<String, String> nameOf) {
        String raw = prefix == null ? "" : prefix.trim();
        String p = raw.toLowerCase();
        List<Candidate> out = new ArrayList<>();

        if (p.isEmpty() || WILDCARD.equals(raw)) {
            out.add(
                    Candidate.of(
                            WILDCARD, Component.translatable("preciseglower.all").getString()));
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
