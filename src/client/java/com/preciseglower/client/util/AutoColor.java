package com.preciseglower.client.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class AutoColor {
    private static final float HUE_JITTER = 24.0F;

    private static final float BASE_SAT = 0.78F;
    private static final float BASE_VAL = 1.0F;

    private static final Map<String, Float> MOB_HUES =
            Map.ofEntries(
                    Map.entry("monster", 0.0F),
                    Map.entry("creature", 125.0F),
                    Map.entry("ambient", 282.0F),
                    Map.entry("axolotls", 332.0F),
                    Map.entry("underground_water_creature", 165.0F),
                    Map.entry("water_creature", 193.0F),
                    Map.entry("water_ambient", 213.0F),
                    Map.entry("misc", 45.0F));

    private static final float MOB_NONE_HUE = 185.0F;

    private static final Map<String, Float> TAB_HUES =
            Map.ofEntries(
                    Map.entry("building_blocks", 30.0F),
                    Map.entry("colored_blocks", 300.0F),
                    Map.entry("natural_blocks", 105.0F),
                    Map.entry("functional_blocks", 20.0F),
                    Map.entry("redstone_blocks", 355.0F),
                    Map.entry("tools_and_utilities", 212.0F),
                    Map.entry("combat", 340.0F),
                    Map.entry("food_and_drinks", 40.0F),
                    Map.entry("ingredients", 55.0F),
                    Map.entry("spawn_eggs", 272.0F),
                    Map.entry("op_blocks", 258.0F));

    private static final float TAB_NONE_HUE = 185.0F;

    private AutoColor() {}

    public static int forBlock(String id) {
        try {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id.trim()));
            if (block != null) {
                int map = block.defaultMapColor().col;
                if (map != 0) return map | 0xFF000000;
            }
        } catch (Exception ignored) {
        }
        return Colors.hashColor(id);
    }

    public static int forEntity(String id) {
        String key = id == null ? "" : id.trim();
        String category = mobCategoryOf(key);
        Float hue = MOB_HUES.get(category);
        if (hue == null) {
            return tint(key, MOB_NONE_HUE, 90.0F);
        }
        return tint(key, hue, HUE_JITTER);
    }

    public static int forItem(String id) {
        String key = id == null ? "" : id.trim();
        String tab = creativeTabOf(key);
        Float hue = tab == null ? null : TAB_HUES.get(shortName(tab));
        if (hue == null) {
            return tint(key, TAB_NONE_HUE, 70.0F);
        }
        return tint(key, hue, HUE_JITTER);
    }

    private static int tint(String id, float baseHue, float jitter) {
        int hash = id.hashCode();
        float jh = ((hash >>> 16) & 0xFFFF) / 65535.0F - 0.5F;
        float js = ((hash >>> 8) & 0xFF) / 255.0F - 0.5F;
        float jv = (hash & 0xFF) / 255.0F - 0.5F;

        float hue = baseHue + jh * jitter;
        float sat = clamp(BASE_SAT + js * 0.16F, 0.42F, 1.0F);
        float val = clamp(BASE_VAL + jv * 0.14F, 0.78F, 1.0F);
        return Colors.fromHsv(hue, sat, val, 255);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shortName(String location) {
        int i = location.indexOf(':');
        return i < 0 ? location : location.substring(i + 1);
    }

    private static String mobCategoryOf(String id) {
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
            if (type == null) return "";
            MobCategory category = type.getCategory();
            return category == null ? "" : category.getName();
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, String> itemTabIndex;

    public static void invalidate() {
        itemTabIndex = null;
    }

    private static String creativeTabOf(String id) {
        Map<String, String> index = itemTabIndex;
        if (index == null) {
            index = buildTabIndex();
            itemTabIndex = index;
        }
        return index.get(id);
    }

    private static Map<String, String> buildTabIndex() {
        Map<String, String> out = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        try {
            CreativeModeTab.ItemDisplayParameters params =
                    new CreativeModeTab.ItemDisplayParameters(
                            mc.level.enabledFeatures(), true, mc.level.registryAccess());
            for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                if (key == null) continue;
                tab.buildContents(params);
                for (ItemStack stack : tab.getDisplayItems()) {
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    Identifier itemKey = BuiltInRegistries.ITEM.getKey(item);
                    if (itemKey == null) continue;
                    out.putIfAbsent(itemKey.toString(), key.toString());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
