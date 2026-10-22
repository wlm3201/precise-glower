package com.preciseglower.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.preciseglower.client.render.HiddenAlpha;
import com.preciseglower.client.render.HideStateHook;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @WrapMethod(method = "submit")
    private void preciseglower$submitHiddenAlpha(
            EntityRenderState renderState,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Operation<Void> original) {
        HiddenAlpha.begin(
                renderState instanceof HideStateHook hook ? hook.preciseglower$getAlpha() : 1F);
        try {
            original.call(renderState, camera, x, y, z, poseStack, submitNodeCollector);
        } finally {
            HiddenAlpha.end();
        }
    }
}
