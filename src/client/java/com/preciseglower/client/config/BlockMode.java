package com.preciseglower.client.config;

public enum BlockMode {
    WIREFRAME,
    OUTLINE,
    BOTH,
    NONE;

    public boolean wireframe() {
        return this == WIREFRAME || this == BOTH;
    }

    public boolean outline() {
        return this == OUTLINE || this == BOTH;
    }

    public static BlockMode of(boolean wireframe, boolean outline) {
        if (wireframe) return outline ? BOTH : WIREFRAME;
        return outline ? OUTLINE : NONE;
    }
}
