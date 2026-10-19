package com.preciseglower.client.search;

import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.match.EntityNbt;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 隐藏追踪：判定某个实体 / 掉落物是否被「隐藏规则」命中。
 * <ul>
 *     <li>实体规则：类型（可用 {@code *} 通配）+ 可选 NBT，例 {@code *{IsBaby:true}}</li>
 *     <li>物品规则：物品 id + 可选 NBT，只作用于地上的掉落物（{@link ItemEntity}）</li>
 *     <li>每 tick 生成一张新快照并整体替换，渲染线程只读快照，避免读写同一张表</li>
 * </ul>
 * 命中结果同时用于「不渲染」和「准心不可选中」两处。
 */
public final class HideTracker {
	private static final Map<Entity, Rule> EMPTY = Map.of();

	private List<EntityRule> entityRules = new ArrayList<>();
	private List<ItemRule> itemRules = new ArrayList<>();
	/** 是否有任何一条启用的隐藏规则；没有时整个追踪器是空转的 */
	private boolean anyEnabled;

	/** 实体 -> 命中的那条隐藏规则（渲染时还要读它的不透明度 / 忽略交互） */
	private volatile Map<Entity, Rule> hidden = EMPTY;

	public void setRules(@Nullable List<EntityRule> entityRules, @Nullable List<ItemRule> itemRules) {
		this.entityRules = entityRules == null ? new ArrayList<>() : entityRules;
		this.itemRules = itemRules == null ? new ArrayList<>() : itemRules;
		this.anyEnabled = false;
		for (EntityRule rule : this.entityRules) {
			if (rule.enabled) {
				this.anyEnabled = true;
				break;
			}
		}
		if (!this.anyEnabled) {
			for (ItemRule rule : this.itemRules) {
				if (rule.enabled) {
					this.anyEnabled = true;
					break;
				}
			}
		}
		if (!this.anyEnabled) hidden = EMPTY;
	}

	public void clear() {
		hidden = EMPTY;
	}

	/** 每 tick 重建快照（只在确实有启用规则时才有开销） */
	public void onTick(@Nullable ClientLevel level) {
		if (level == null || !anyEnabled) {
			hidden = EMPTY;
			return;
		}
		IdentityHashMap<Entity, Rule> snapshot = new IdentityHashMap<>();
		HolderLookup.Provider registries = level.registryAccess();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == null || !entity.isAlive()) continue;
			Rule rule = matches(entity, registries);
			if (rule != null) snapshot.put(entity, rule);
		}
		hidden = snapshot;
	}

	/** 命中了某条隐藏规则（无论它是完全隐藏还是半透明） */
	public boolean isHidden(@Nullable Entity entity) {
		return matched(entity) != null;
	}

	/**
	 * 该实体实际的不透明度：1 = 不受影响；0 = 完全隐藏（不渲染）；
	 * 中间值表示要按半透明渲染。
	 */
	public float opacityFor(@Nullable Entity entity) {
		Rule rule = matched(entity);
		return rule == null ? 1F : rule.opacity01();
	}

	/** 准心 / 攻击是否要跳过这个实体：完全隐藏的一定跳过，其余看规则上的开关 */
	public boolean skipsInteraction(@Nullable Entity entity) {
		Rule rule = matched(entity);
		if (rule == null) return false;
		return rule.opacity01() <= 0F || rule.ignoreInteraction;
	}

	private @Nullable Rule matched(@Nullable Entity entity) {
		if (entity == null || !anyEnabled) return null;
		Rule cached = hidden.get(entity);
		if (cached != null) return cached;
		if (hidden.containsKey(entity)) return null;
		// 不在 tick 快照里（刚生成、或尚未进入渲染列表）：现算一次，不写回快照
		return entity.level() == null ? null : matches(entity, entity.level().registryAccess());
	}

	private @Nullable Rule matches(Entity entity, HolderLookup.Provider registries) {
		CompoundTag entityTag = null;

		for (int i = 0; i < entityRules.size(); i++) {
			EntityRule rule = entityRules.get(i);
			if (!rule.enabled || rule.error() != null) continue;
			if (!rule.matchesType(entity)) continue;
			if (rule.matcher().isAny()) return rule;
			if (entityTag == null) entityTag = EntityNbt.of(entity, registries);
			if (entityTag != null && rule.matcher().matches(entityTag)) return rule;
		}

		if (entity instanceof ItemEntity itemEntity) {
			ItemStack stack = itemEntity.getItem();
			if (!stack.isEmpty()) {
				CompoundTag stackTag = null;
				for (int i = 0; i < itemRules.size(); i++) {
					ItemRule rule = itemRules.get(i);
					if (!rule.enabled || rule.error() != null) continue;
					if (!rule.isWildcard() && rule.itemType() != stack.getItem()) continue;
					if (stack.getCount() < rule.minCount()) continue;
					if (rule.matcher().isAny()) return rule;
					if (stackTag == null) stackTag = EntityNbt.of(stack, registries);
					if (stackTag != null && rule.matcher().matches(stackTag)) return rule;
				}
			}
		}
		return null;
	}
}
