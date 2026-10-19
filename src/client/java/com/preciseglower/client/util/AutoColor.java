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

/**
 * 添加规则时的自动初始颜色。
 *
 * <p><b>分组取自游戏，配色由我们定</b>。
 * 每个分组给一个基础色相，组内再按 id 哈希做小幅抖动 —— 于是同一组看起来是一个色系，
 * 但组里不同成员又能区分开。
 *
 * <ul>
 *     <li>方块：地图色 {@code Block#defaultMapColor()}（最贴近方块本身的颜色）</li>
 *     <li>实体：{@code EntityType#getCategory()} 的生物上限分组（MONSTER / CREATURE / …）</li>
 *     <li>物品：创造模式物品栏 {@code CreativeModeTab}</li>
 * </ul>
 */
public final class AutoColor {
	/** 组内色相抖动幅度（度） */
	private static final float HUE_JITTER = 24.0F;
	private static final float BASE_SAT = 0.78F;
	private static final float BASE_VAL = 1.0F;

	// ------------------------------------------------------------------ 色相表

	/** 实体：按生物上限分组定色相，语义是「威胁度 + 栖息环境」 */
	private static final Map<String, Float> MOB_HUES = Map.ofEntries(
		Map.entry("monster", 0.0F),                          // 敌对 → 红
		Map.entry("creature", 125.0F),                       // 被动 → 绿
		Map.entry("ambient", 282.0F),                        // 环境（蝙蝠）→ 紫
		Map.entry("axolotls", 332.0F),                       // 美西螈 → 粉
		Map.entry("underground_water_creature", 165.0F),     // 地下水生 → 青绿
		Map.entry("water_creature", 193.0F),                 // 水生（海豚/鱿鱼）→ 青
		Map.entry("water_ambient", 213.0F),                  // 水中环境（鱼）→ 蓝
		Map.entry("misc", 45.0F)                             // 其他生物 → 琥珀
	);

	/** 实体没有生物分类时（箭 / 船 / 矿车 / 玩家…）的中性色相 */
	private static final float MOB_NONE_HUE = 185.0F;

	/** 物品：按创造物品栏定色相 */
	private static final Map<String, Float> TAB_HUES = Map.ofEntries(
		Map.entry("building_blocks", 30.0F),                 // 建筑 → 暖木/沙
		Map.entry("colored_blocks", 300.0F),                 // 染色 → 品红
		Map.entry("natural_blocks", 105.0F),                 // 自然 → 草绿
		Map.entry("functional_blocks", 20.0F),               // 功能 → 橙棕
		Map.entry("redstone_blocks", 355.0F),                // 红石 → 红
		Map.entry("tools_and_utilities", 212.0F),            // 工具 → 钢蓝
		Map.entry("combat", 340.0F),                         // 战斗 → 绯红
		Map.entry("food_and_drinks", 40.0F),                 // 食物 → 橙黄
		Map.entry("ingredients", 55.0F),                     // 材料 → 黄
		Map.entry("spawn_eggs", 272.0F),                     // 刷怪蛋 → 紫
		Map.entry("op_blocks", 258.0F)                       // 管理员 → 深紫
	);

	/** 物品栏未知 / 不在创造栏时的兜底色相 */
	private static final float TAB_NONE_HUE = 185.0F;

	private AutoColor() {
	}

	// ------------------------------------------------------------------ 入口

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
			// 没有生物分类：中性色相 + 更大抖动，保证彼此还能区分
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

	// ------------------------------------------------------------------ 取色

	/** 基色相 + id 哈希抖动：同组一个色系，组内又有细微差别 */
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

	// ------------------------------------------------------- 分组查询

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

	/** 物品 -> 创造物品栏，索引懒加载一次 */
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

	/**
	 * 遍历所有创造物品栏、临时构建其内容，得到「物品 -> 物品栏」的映射。
	 * 需要已进入世界（拿得到 registryAccess 与 feature flags），否则返回空表走兜底。
	 */
	private static Map<String, String> buildTabIndex() {
		Map<String, String> out = new HashMap<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return out;
		try {
			CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(
				mc.level.enabledFeatures(), true, mc.level.registryAccess()
			);
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
			// 构建失败就退化成哈希取色，不影响使用
		}
		return out;
	}
}
