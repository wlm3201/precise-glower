package com.preciseglower.client.search;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.match.EntityNbt;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class EntityTracker {
    private final Map<Entity, Rule> matched = new IdentityHashMap<>();

    private final Map<Entity, Rule> boxes = new IdentityHashMap<>();

    private int[] entityCounts = new int[0];
    private int[] itemCounts = new int[0];

    private List<EntityRule> entityRules = new ArrayList<>();
    private List<ItemRule> itemRules = new ArrayList<>();

    private List<Rule> candidates = new ArrayList<>();

    private int maxDistance = 160;

    private int coarseRangeBlocks = 160;

    private int maxRadiusChunks = 9;

    public void setRules(List<EntityRule> entityRules, List<ItemRule> itemRules) {
        this.entityRules = entityRules;
        this.itemRules = itemRules;
        this.entityCounts = new int[entityRules.size()];
        this.itemCounts = new int[itemRules.size()];
        List<Rule> merged = new ArrayList<>(itemRules.size() + entityRules.size());
        merged.addAll(itemRules);
        merged.addAll(entityRules);
        this.candidates = merged;
        recomputeRange();
        clear();
    }

    public void setMaxDistance(int maxDistance) {
        this.maxDistance = Math.max(8, maxDistance);
        recomputeRange();
    }

    private void recomputeRange() {
        int maxChunks = 1;
        for (EntityRule rule : entityRules) {
            maxChunks = Math.max(maxChunks, Math.min(globalRangeChunks(), rule.rangeChunks()));
        }
        for (ItemRule rule : itemRules) {
            maxChunks = Math.max(maxChunks, Math.min(globalRangeChunks(), rule.rangeChunks()));
        }
        maxRadiusChunks = maxChunks - 1;
        coarseRangeBlocks = Math.max(8, (maxRadiusChunks + 1) * 16);
    }

    private int globalRangeChunks() {
        return Math.max(1, maxDistance >> 4);
    }

    private boolean withinRange(Entity entity, Rule rule, int pcx, int pcz) {
        int radius = Math.min(globalRangeChunks(), rule.rangeChunks()) - 1;
        int ecx = (int) Math.floor(entity.getX()) >> 4;
        int ecz = (int) Math.floor(entity.getZ()) >> 4;
        return Math.abs(ecx - pcx) <= radius && Math.abs(ecz - pcz) <= radius;
    }

    public void clear() {
        matched.clear();
        boxes.clear();
    }

    public int entityCount(int index) {
        return index >= 0 && index < entityCounts.length ? entityCounts[index] : 0;
    }

    public int itemCount(int index) {
        return index >= 0 && index < itemCounts.length ? itemCounts[index] : 0;
    }

    public int totalMatched() {
        return matched.size();
    }

    public @Nullable Rule ruleFor(Entity entity) {
        return matched.get(entity);
    }

    public int colorFor(Entity entity, Rule rule) {
        return rule == null ? 0xFFFFFFFF : rule.color;
    }

    public void onTick(ClientLevel level) {
        matched.clear();
        boxes.clear();
        if (level == null) return;
        if (entityRules.isEmpty() && itemRules.isEmpty()) return;

        entityCounts = new int[entityRules.size()];
        itemCounts = new int[itemRules.size()];
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 cam = mc.player.position();
        int pcx = (int) Math.floor(cam.x) >> 4;
        int pcz = (int) Math.floor(cam.z) >> 4;
        double coarseDistSqr = (double) coarseRangeBlocks * coarseRangeBlocks;
        HolderLookup.Provider registries = level.registryAccess();

        HideTracker hideTracker = PreciseGlowerClient.hideTracker();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive()) continue;
            double distSqr = entity.distanceToSqr(cam);
            if (distSqr > coarseDistSqr) continue;
            if (hideTracker != null && hideTracker.isHidden(entity)) continue;
            Rule rule = match(entity, registries, pcx, pcz);
            if (rule == null) continue;
            matched.put(entity, rule);
            if (rule.showBox) boxes.put(entity, rule);
            if (rule instanceof EntityRule er) {
                int i = entityRules.indexOf(er);
                if (i >= 0) entityCounts[i]++;
            } else if (rule instanceof ItemRule ir) {
                int i = itemRules.indexOf(ir);
                if (i >= 0) itemCounts[i]++;
            }
        }
    }

    private @Nullable Rule match(
            Entity entity, HolderLookup.Provider registries, int pcx, int pcz) {
        CompoundTag entityTag = null;
        CompoundTag stackTag = null;
        boolean itemEntity = entity instanceof ItemEntity;
        ItemStack stack = itemEntity ? ((ItemEntity) entity).getItem() : null;
        boolean itemVisible = stack != null && !stack.isEmpty();

        for (int i = 0; i < candidates.size(); i++) {
            Rule candidate = candidates.get(i);
            if (!candidate.enabled || candidate.error() != null) continue;
            if (!withinRange(entity, candidate, pcx, pcz)) continue;
            if (candidate instanceof EntityRule rule) {
                if (!rule.matchesType(entity)) continue;
                if (rule.matcher().isAny()) return rule;
                if (entityTag == null) entityTag = EntityNbt.of(entity, registries);
                if (entityTag != null && rule.matcher().matches(entityTag)) return rule;
            } else if (itemVisible) {
                ItemRule rule = (ItemRule) candidate;
                if (!rule.isWildcard() && rule.itemType() != stack.getItem()) continue;
                if (stack.getCount() < rule.minCount()) continue;
                if (rule.matcher().isAny()) return rule;
                if (stackTag == null) stackTag = EntityNbt.of(stack, registries);
                if (stackTag != null && rule.matcher().matches(stackTag)) return rule;
            }
        }
        return null;
    }

    public void renderBoxes(float partialTick) {
        if (boxes.isEmpty()) return;
        for (Map.Entry<Entity, Rule> entry : boxes.entrySet()) {
            Entity entity = entry.getKey();
            Rule rule = entry.getValue();
            AABB box = entity.getBoundingBox();
            if (box == null) continue;

            Vec3 lerped = entity.getPosition(partialTick);
            Vec3 now = entity.position();
            box = box.move(lerped.x - now.x, lerped.y - now.y, lerped.z - now.z);

            net.minecraft.gizmos.GizmoProperties props =
                    net.minecraft.gizmos.Gizmos.cuboid(
                            box,
                            net.minecraft.gizmos.GizmoStyle.stroke(
                                    colorFor(entity, rule), rule.lineWidth));
            props.setAlwaysOnTop();
        }
    }
}
