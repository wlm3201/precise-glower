package com.preciseglower.client.gui.autocomplete;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class NameIndex {
    private static final Map<String, String> BLOCKS = new HashMap<>();
    private static final Map<String, String> ENTITIES = new HashMap<>();
    private static final Map<String, String> ITEMS = new HashMap<>();

    private NameIndex() {}

    public static void rebuild() {
        BLOCKS.clear();
        ENTITIES.clear();
        ITEMS.clear();
        try {
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier key = BuiltInRegistries.BLOCK.getKey(block);
                if (key == null || !"minecraft".equals(key.getNamespace())) continue;
                BLOCKS.put(key.toString(), block.getName().getString());
            }
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (key == null || !"minecraft".equals(key.getNamespace())) continue;
                ENTITIES.put(key.toString(), type.getDescription().getString());
            }
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier key = BuiltInRegistries.ITEM.getKey(item);
                if (key == null || !"minecraft".equals(key.getNamespace())) continue;
                ITEMS.put(
                        key.toString(),
                        Component.translatable(item.getDescriptionId()).getString());
            }
        } catch (Exception ignored) {
        }
    }

    public static String blockName(String id) {
        return BLOCKS.getOrDefault(id, "");
    }

    public static String entityName(String id) {
        return ENTITIES.getOrDefault(id, "");
    }

    public static String itemName(String id) {
        return ITEMS.getOrDefault(id, "");
    }
}
