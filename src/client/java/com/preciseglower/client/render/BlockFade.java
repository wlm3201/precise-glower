package com.preciseglower.client.render;

import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

public final class BlockFade {
    private static final Map<Object, Float> OWNED = new WeakHashMap<>();
    private static final ThreadLocal<Float> DRAW = ThreadLocal.withInitial(() -> 1F);

    private BlockFade() {}

    public static void markMoving(@Nullable Object movingBlockRenderState, float alpha) {
        if (movingBlockRenderState != null && alpha < 1F) OWNED.put(movingBlockRenderState, alpha);
    }

    public static float movingAlphaOf(@Nullable Object movingBlockRenderState) {
        Float alpha = movingBlockRenderState == null ? null : OWNED.get(movingBlockRenderState);
        return alpha == null ? 1F : alpha;
    }

    public static void begin(float alpha) {
        DRAW.set(alpha);
    }

    public static void end() {
        DRAW.set(1F);
    }

    public static float current() {
        return DRAW.get();
    }
}
