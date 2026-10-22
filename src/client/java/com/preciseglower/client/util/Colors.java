package com.preciseglower.client.util;

import java.util.OptionalInt;

public final class Colors {
    public static final int WHITE = 0xFFFFFFFF;

    public static final int[] PRESET = {
        0xFFFFFFFF,
        0xFFFF8000,
        0xFFFF00FF,
        0xFF0080FF,
        0xFFFFFF00,
        0xFF008000,
        0xFFFF80C0,
        0xFFC0FF00,
        0xFF00FF80,
        0xFF00FFFF,
        0xFF8000FF,
        0xFF0000FF,
        0xFF804020,
        0xFF00FF00,
        0xFFFF0000,
        0xFF000000
    };

    private Colors() {}

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int hashColor(String key) {
        int hash = key == null ? 0 : key.hashCode();
        float hue = ((hash >>> 16) & 0xFFFF) / 65536.0F * 360.0F;
        float sat = 0.65F + ((hash >>> 8) & 0xFF) / 255.0F * 0.3F;
        return fromHsv(hue, sat, 1.0F, 255);
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    public static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int withAlpha(int rgb, int alpha) {
        return rgb & 0x00FFFFFF | (alpha & 0xFF) << 24;
    }

    public static String toHex(int argb) {
        return "%02X%02X%02X%02X".formatted(red(argb), green(argb), blue(argb), alpha(argb));
    }

    public static OptionalInt fromHex(String text) {
        if (text == null) return OptionalInt.empty();
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            StringBuilder sb = new StringBuilder(6);
            for (char c : s.toCharArray()) sb.append(c).append(c);
            s = sb.toString();
        }
        try {
            if (s.length() == 6) {
                long rgb = Long.parseLong(s, 16);
                return OptionalInt.of((int) (0xFF000000L | rgb));
            }
            if (s.length() == 8) {
                long v = Long.parseLong(s, 16);
                int r = (int) (v >>> 24) & 0xFF;
                int g = (int) (v >>> 16) & 0xFF;
                int b = (int) (v >>> 8) & 0xFF;
                int a = (int) v & 0xFF;
                return OptionalInt.of(argb(a, r, g, b));
            }
            return OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static float[] toHsv(int argb) {
        float r = red(argb) / 255.0F;
        float g = green(argb) / 255.0F;
        float b = blue(argb) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0.0F;
        if (d != 0.0F) {
            if (max == r) h = ((g - b) / d) % 6.0F;
            else if (max == g) h = (b - r) / d + 2.0F;
            else h = (r - g) / d + 4.0F;
            h *= 60.0F;
            if (h < 0.0F) h += 360.0F;
        }
        float s = max == 0.0F ? 0.0F : d / max;
        return new float[] {h, s, max};
    }

    public static int fromHsv(float h, float s, float v, int alpha) {
        return withAlpha(hsvToRgb(h, s, v), alpha);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = ((h % 360.0F) + 360.0F) % 360.0F / 60.0F;
        int sector = (int) Math.floor(hh);
        float f = hh - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        return switch (sector % 6) {
            case 0 -> rgb((int) (v * 255), (int) (t * 255), (int) (p * 255));
            case 1 -> rgb((int) (q * 255), (int) (v * 255), (int) (p * 255));
            case 2 -> rgb((int) (p * 255), (int) (v * 255), (int) (t * 255));
            case 3 -> rgb((int) (p * 255), (int) (q * 255), (int) (v * 255));
            case 4 -> rgb((int) (t * 255), (int) (p * 255), (int) (v * 255));
            default -> rgb((int) (v * 255), (int) (p * 255), (int) (q * 255));
        };
    }

    public static boolean isLight(int argb) {
        return (red(argb) * 299 + green(argb) * 587 + blue(argb) * 114) / 1000 >= 128;
    }
}
