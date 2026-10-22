package com.preciseglower.client.render;

public final class HiddenAlpha {
    private static final ThreadLocal<Float> CURRENT = ThreadLocal.withInitial(() -> 1F);

    private HiddenAlpha() {}

    public static float current() {
        return CURRENT.get();
    }

    public static void begin(float alpha) {
        CURRENT.set(alpha);
    }

    public static void end() {
        CURRENT.set(1F);
    }

    public static int apply(int argb, float alpha) {
        if (alpha >= 1F) return argb;
        if (alpha <= 0F) return argb & 0x00FFFFFF;
        int a = Math.round(255 * alpha);
        return (argb & 0x00FFFFFF) | (a << 24);
    }
}
