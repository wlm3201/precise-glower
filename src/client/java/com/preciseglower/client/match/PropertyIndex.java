package com.preciseglower.client.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public final class PropertyIndex {
    private static volatile Map<String, List<String>> index;

    private PropertyIndex() {}

    private static Map<String, List<String>> get() {
        Map<String, List<String>> result = index;
        if (result == null) {
            synchronized (PropertyIndex.class) {
                result = index;
                if (result == null) {
                    result = build();
                    index = result;
                }
            }
        }
        return result;
    }

    private static Map<String, List<String>> build() {
        Map<String, TreeSet<String>> values = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                values.computeIfAbsent(property.getName(), k -> new TreeSet<>())
                        .addAll(
                                property.getPossibleValues().stream()
                                        .map(String::valueOf)
                                        .toList());
            }
        }
        Map<String, List<String>> out = new TreeMap<>();
        values.forEach((name, set) -> out.put(name, List.copyOf(set)));
        return out;
    }

    public static List<String> names() {
        return new ArrayList<>(get().keySet());
    }

    public static List<String> values(String name) {
        return get().getOrDefault(name, List.of());
    }
}
