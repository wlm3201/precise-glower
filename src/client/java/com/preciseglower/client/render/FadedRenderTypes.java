package com.preciseglower.client.render;

import com.preciseglower.client.mixin.RenderTypeAccess;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;

public final class FadedRenderTypes {
    private static final Map<Integer, RenderType> BY_ALPHA = new HashMap<>();

    private static final Map<RenderType, Float> ALPHAS = new HashMap<>();

    private FadedRenderTypes() {}

    public static RenderType faded(float alpha) {
        int a = Math.round(255 * Math.max(0F, Math.min(1F, alpha)));
        RenderType type = BY_ALPHA.get(a);
        if (type == null) {
            type = RenderType.create("preciseglower_faded_block_" + a, baseSetup());
            BY_ALPHA.put(a, type);
            ALPHAS.put(type, a / 255F);
        }
        return type;
    }

    public static float alphaOf(@Nullable RenderType type) {
        Float alpha = type == null ? null : ALPHAS.get(type);
        return alpha == null ? 1F : alpha;
    }

    private static RenderSetup baseSetup() {
        return ((RenderTypeAccess) (Object) Sheets.translucentBlockItemSheet())
                .preciseglower$state();
    }
}
