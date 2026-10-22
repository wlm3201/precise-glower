package com.preciseglower.client.config;

import com.preciseglower.client.util.Colors;

public abstract class Rule {
    public boolean enabled = true;

    public int color = 0xFF00FF80;

    public float lineWidth = 2.0F;

    public boolean alwaysOnTop = true;

    public boolean glow = true;

    public boolean showBox = false;

    public ColorMode colorMode = ColorMode.AUTO;

    public boolean mergeAdjacent = true;

    public BlockMode blockMode = BlockMode.WIREFRAME;

    public int rangeChunks = DEFAULT_RANGE_CHUNKS;

    public int opacity = 0;

    public boolean ignoreInteraction = true;

    public float opacity01() {
        if (opacity <= 0) return 0F;
        if (opacity >= 100) return 1F;
        return opacity / 100F;
    }

    public BlockMode blockMode() {
        return blockMode == null ? BlockMode.WIREFRAME : blockMode;
    }

    public static final int MIN_RANGE_CHUNKS = 1;
    public static final int MAX_RANGE_CHUNKS = 16;
    public static final int DEFAULT_RANGE_CHUNKS = 16;

    public int rangeChunks() {
        if (rangeChunks < MIN_RANGE_CHUNKS) return DEFAULT_RANGE_CHUNKS;
        return Math.min(rangeChunks, MAX_RANGE_CHUNKS);
    }

    public void setRangeChunks(int chunks) {
        rangeChunks = Math.max(MIN_RANGE_CHUNKS, Math.min(chunks, MAX_RANGE_CHUNKS));
    }

    public abstract String key();

    public abstract String displayName();

    public abstract String filterText();

    public abstract String error();

    public abstract void compile();

    public String filterSummary() {
        String text = filterText();
        return text == null || text.isBlank() ? "" : text;
    }

    public int specificity() {
        int score = isWildcard() ? 0 : 2;
        String filter = filterText();
        if (filter != null && !filter.isBlank()) score++;
        return score;
    }

    public int opaqueColor() {
        return color | 0xFF000000;
    }

    public boolean isWildcard() {
        return false;
    }

    public static int defaultColor(int index) {
        return Colors.PRESET[Math.floorMod(index, Colors.PRESET.length)];
    }

    public Rule copy() {
        Rule clone = newInstance();
        clone.enabled = this.enabled;
        clone.color = this.color;
        clone.lineWidth = this.lineWidth;
        clone.alwaysOnTop = this.alwaysOnTop;
        clone.glow = this.glow;
        clone.showBox = this.showBox;
        clone.colorMode = this.colorMode;
        clone.mergeAdjacent = this.mergeAdjacent;
        clone.blockMode = this.blockMode;
        clone.rangeChunks = this.rangeChunks;
        clone.opacity = this.opacity;
        clone.ignoreInteraction = this.ignoreInteraction;
        copyInto(clone);
        return clone;
    }

    protected abstract Rule newInstance();

    protected abstract void copyInto(Rule target);
}
