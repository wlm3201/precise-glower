package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.preciseglower.client.render.BlockFade;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MovingBlockFeatureRenderer.class)
public class MovingBlockFeatureRendererMixin {
    private static final String BUILD_GROUP =
            "buildGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Ljava/util/List;)V";

    @WrapMethod(method = BUILD_GROUP)
    private void preciseglower$fadeGroups(
            FeatureFrameContext context, List<?> submits, Operation<Void> original) {
        Map<Float, List<Object>> groups = new LinkedHashMap<>();
        for (Object submit : submits) {
            groups.computeIfAbsent(
                            BlockFade.movingAlphaOf(stateOf(submit)), key -> new ArrayList<>())
                    .add(submit);
        }
        if (groups.size() == 1 && groups.containsKey(1F)) {
            original.call(context, submits);
            return;
        }
        for (Map.Entry<Float, List<Object>> group : groups.entrySet()) {
            BlockFade.begin(group.getKey());
            original.call(context, group.getValue());
        }
        BlockFade.end();
    }

    private static Object stateOf(Object submit) {
        return submit instanceof MovingBlockFeatureRenderer.Submit moving
                ? moving.movingBlockRenderState()
                : null;
    }

    private static final String PUT_BAKED_QUAD_METHOD =
            "putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack;FFF"
                    + "Lnet/minecraft/client/resources/model/geometry/BakedQuad;"
                    + "Lcom/mojang/blaze3d/vertex/QuadInstance;"
                    + "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;I)V";
    private static final String GET_VERTEX_BUILDER =
            "Lnet/minecraft/client/renderer/feature/MovingBlockFeatureRenderer;getVertexBuilder("
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;)"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;";
    private static final String PUT_BAKED_QUAD_CALL =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lnet/minecraft/client/resources/model/geometry/BakedQuad;"
                    + "Lcom/mojang/blaze3d/vertex/QuadInstance;)V";

    @ModifyArg(
            method = PUT_BAKED_QUAD_METHOD,
            at = @At(value = "INVOKE", target = GET_VERTEX_BUILDER),
            index = 0)
    private static RenderType preciseglower$translucentLayer(RenderType renderType) {
        return BlockFade.current() < 1F ? RenderTypes.translucentMovingBlock() : renderType;
    }

    @WrapOperation(
            method = PUT_BAKED_QUAD_METHOD,
            at = @At(value = "INVOKE", target = PUT_BAKED_QUAD_CALL))
    private static void preciseglower$quadAlpha(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            BakedQuad quad,
            QuadInstance instance,
            Operation<Void> original) {
        original.call(buffer, pose, quad, instance);
    }
}
