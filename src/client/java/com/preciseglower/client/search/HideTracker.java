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

public final class HideTracker {
    private static final Map<Entity, Rule> EMPTY = Map.of();

    private List<EntityRule> entityRules = new ArrayList<>();
    private List<ItemRule> itemRules = new ArrayList<>();

    private boolean anyEnabled;

    private volatile Map<Entity, Rule> hidden = EMPTY;

    public void setRules(
            @Nullable List<EntityRule> entityRules, @Nullable List<ItemRule> itemRules) {
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

    public boolean isHidden(@Nullable Entity entity) {
        return matched(entity) != null;
    }

    public float opacityFor(@Nullable Entity entity) {
        Rule rule = matched(entity);
        return rule == null ? 1F : rule.opacity01();
    }

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
