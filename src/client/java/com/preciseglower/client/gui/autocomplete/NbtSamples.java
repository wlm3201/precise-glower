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

public final class NbtSamples {
    private static final int MAX = 16;

    private static final Map<EntityType<?>, List<CompoundTag>> ENTITY_TEMPLATES =
            new ConcurrentHashMap<>();

    private static final Map<Item, List<CompoundTag>> ITEM_TEMPLATES = new ConcurrentHashMap<>();

    private NbtSamples() {}

    public static List<CompoundTag> forEntity(@Nullable EntityType<?> type) {
        List<CompoundTag> out = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Set<EntityType<?>> seen = new HashSet<>();
            List<Entity> repeats = new ArrayList<>();
            for (Entity entity : level.entitiesForRendering()) {
                if (type != null && entity.getType() != type) continue;
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

    private static boolean addSample(List<CompoundTag> out, Entity entity, ClientLevel level) {
        CompoundTag tag = EntityNbt.of(entity, level.registryAccess());
        if (tag != null) out.add(tag);
        return out.size() >= MAX;
    }

    private static boolean addStackSample(
            List<CompoundTag> out, ItemStack stack, ClientLevel level) {
        CompoundTag tag = EntityNbt.of(stack, level.registryAccess());
        if (tag != null) out.add(tag);
        return out.size() >= MAX;
    }

    private static List<CompoundTag> templateForEntity(@Nullable EntityType<?> type) {
        if (type == null) return List.of();
        return ENTITY_TEMPLATES.computeIfAbsent(
                type,
                key -> {
                    try {
                        ClientLevel level = Minecraft.getInstance().level;
                        if (level == null) return List.of();
                        Entity entity = key.create(level, EntitySpawnReason.COMMAND);
                        if (entity == null) return List.of();
                        CompoundTag tag = EntityNbt.of(entity, level.registryAccess());
                        return tag == null ? List.of() : List.of(tag);
                    } catch (Throwable t) {
                        return List.of();
                    }
                });
    }

    private static List<CompoundTag> templateForItem(@Nullable Item item) {
        if (item == null) return List.of();
        return ITEM_TEMPLATES.computeIfAbsent(
                item,
                key -> {
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
