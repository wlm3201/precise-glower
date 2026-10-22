package com.preciseglower.client.mixin;

import com.preciseglower.client.render.HiddenAlpha;
import com.preciseglower.client.render.HideStateHook;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class HideStateMixin implements HideStateHook {
    @Unique private float preciseglower$alpha = 1F;

    @Override
    public void preciseglower$setAlpha(float alpha) {
        this.preciseglower$alpha = alpha;
    }

    @Override
    public float preciseglower$getAlpha() {
        return preciseglower$alpha > 0F ? preciseglower$alpha : HiddenAlpha.current();
    }
}
