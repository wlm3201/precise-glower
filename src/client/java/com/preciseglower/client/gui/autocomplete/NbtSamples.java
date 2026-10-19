package com.preciseglower.client.gui.autocomplete;

import com.preciseglower.client.match.EntityNbt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 补全候选来源：优先取「世界里真实存在的 NBT」，取不到时再按类型自己造一份默认值。
 *
 * <p>只有真实采样的老做法有个硬伤：写着 {@code minecraft:goat}、可附近一只山羊都没有时，
 * 候选里就只剩到处都有的通用标签（{@code Health}、{@code Air}……），山羊独有的
 * {@code HasLeftHorn} 之类的标签永远不会出现。所以这里加了一层「模板」：
 * 用 {@link EntityType#create} 现造一个该类型的实例（不加入世界）再序列化它的默认 NBT，
 * 于是任何类型、任何种类的实体都能拿到自己那一份字段，包括模组加的实体。</p>
 *
 * <p>模板按类型缓存，且只是构对象 + 序列化：部分实体（比如玩家、以及个别模组实体）
 * 造不出来时返回空列表，退化为「只看世界里现有的」。</p>
 */
public final class NbtSamples {
	private static final int MAX = 16;
	/** 按类型缓存的模板；空列表表示「该类型造不出来」 */
	private static final Map<EntityType<?>, List<CompoundTag>> ENTITY_TEMPLATES = new ConcurrentHashMap<>();
	private static final Map<Item, List<CompoundTag>> ITEM_TEMPLATES = new ConcurrentHashMap<>();

	private NbtSamples() {
	}

	/**
	 * 某个实体类型的 NBT 样本。
	 *
	 * @param type null 表示通配符 {@code *}（尽量每种类型各取一个，让候选覆盖面广一些）
	 */
	public static List<CompoundTag> forEntity(@Nullable EntityType<?> type) {
		List<CompoundTag> out = new ArrayList<>();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			Set<EntityType<?>> seen = new HashSet<>();
			List<Entity> repeats = new ArrayList<>();
			for (Entity entity : level.entitiesForRendering()) {
				if (type != null && entity.getType() != type) continue;
				// 通配符：第一轮每种类型只留一个，重复的先攒着，种类不满再回填
				if (type == null && !seen.add(entity.getType())) {
					repeats.add(entity);
					continue;
				}
				if (addSample(out, entity, level)) break;
			}
			if (type == null) {
				for (Entity entity : repeats) {
					if (addSample(out, entity, level)) break;
				}
			}
		}
		out.addAll(templateForEntity(type));
		return out;
	}

	/** 某个物品的 NBT 样本（作用于地上的掉落物），同样先看世界里现在掉着的东西 */
	public static List<CompoundTag> forItem(@Nullable Item item) {
		List<CompoundTag> out = new ArrayList<>();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			Set<Item> seen = new HashSet<>();
			List<ItemStack> repeats = new ArrayList<>();
			for (Entity entity : level.entitiesForRendering()) {
				if (!(entity instanceof ItemEntity itemEntity)) continue;
				ItemStack stack = itemEntity.getItem();
				if (stack.isEmpty()) continue;
				if (item != null && stack.getItem() != item) continue;
				if (item == null && !seen.add(stack.getItem())) {
					repeats.add(stack);
					continue;
				}
				if (addStackSample(out, stack, level)) break;
			}
			if (item == null) {
				for (ItemStack stack : repeats) {
					if (addStackSample(out, stack, level)) break;
				}
			}
		}
		out.addAll(templateForItem(item));
		return out;
	}

	/** @return true 表示样本已经够多，可以收工 */
	private static boolean addSample(List<CompoundTag> out, Entity entity, ClientLevel level) {
		CompoundTag tag = EntityNbt.of(entity, level.registryAccess());
		if (tag != null) out.add(tag);
		return out.size() >= MAX;
	}

	private static boolean addStackSample(List<CompoundTag> out, ItemStack stack, ClientLevel level) {
		CompoundTag tag = EntityNbt.of(stack, level.registryAccess());
		if (tag != null) out.add(tag);
		return out.size() >= MAX;
	}

	private static List<CompoundTag> templateForEntity(@Nullable EntityType<?> type) {
		if (type == null) return List.of();
		return ENTITY_TEMPLATES.computeIfAbsent(type, key -> {
			try {
				ClientLevel level = Minecraft.getInstance().level;
				if (level == null) return List.of();
				// 只构造不入场：EntityType#create 得到的实例不会加入世界，也不会被 tick
				Entity entity = key.create(level, EntitySpawnReason.COMMAND);
				if (entity == null) return List.of();
				CompoundTag tag = EntityNbt.of(entity, level.registryAccess());
				return tag == null ? List.of() : List.of(tag);
			} catch (Throwable t) {
				// 个别实体（含模组实体）默认构造取不到：静默退化，不能让补全把界面卡死
				return List.of();
			}
		});
	}

	private static List<CompoundTag> templateForItem(@Nullable Item item) {
		if (item == null) return List.of();
		return ITEM_TEMPLATES.computeIfAbsent(item, key -> {
			try {
				ClientLevel level = Minecraft.getInstance().level;
				if (level == null) return List.of();
				CompoundTag tag = EntityNbt.of(new ItemStack(key), level.registryAccess());
				return tag == null ? List.of() : List.of(tag);
			} catch (Throwable t) {
				return List.of();
			}
		});
	}
}
