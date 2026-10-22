package com.preciseglower.client.config;

import java.util.ArrayList;
import java.util.List;

public final class GlowerConfig {
    public List<BlockRule> blocks = new ArrayList<>();
    public List<EntityRule> entities = new ArrayList<>();
    public List<ItemRule> items = new ArrayList<>();

    public List<EntityRule> hiddenEntities = new ArrayList<>();

    public List<ItemRule> hiddenItems = new ArrayList<>();

    public int blockMaxDistance = 256;

    public int entityMaxDistance = 256;

    public int scanBudgetPerTick = 4;

    public int maxRenderedBlocks = 8000;

    public String lastTab = "BLOCK";

    public transient boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }

    public void compileAll() {
        for (BlockRule rule : blocks) rule.compile();
        for (EntityRule rule : entities) rule.compile();
        for (ItemRule rule : items) rule.compile();
        for (EntityRule rule : hiddenEntities) rule.compile();
        for (ItemRule rule : hiddenItems) rule.compile();
        dirty = false;
    }

    public int blockRangeCapChunks() {
        return Math.max(1, blockMaxDistance >> 4);
    }

    public int entityRangeCapChunks() {
        return Math.max(1, entityMaxDistance >> 4);
    }

    public boolean hasAnyEnabled() {
        for (BlockRule r : blocks) if (r.enabled) return true;
        for (EntityRule r : entities) if (r.enabled) return true;
        for (ItemRule r : items) if (r.enabled) return true;
        for (EntityRule r : hiddenEntities) if (r.enabled) return true;
        for (ItemRule r : hiddenItems) if (r.enabled) return true;
        return false;
    }
}
