package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.1.2 / 26.2：蜘蛛眼 / 末影人眼 / 幻翼眼这类「眼睛层」用的是 EYES 管线，既没有混合，
 * 渲染层还是各子类在构造时就缓存好的（换不掉），alpha 完全传不进去 —— 半透明时直接不提交。
 */
@Mixin(EyesLayer.class)
public class EyesLayerMixin {
	private static final String SUBMIT_MODEL =
		"Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
			+ "Lnet/minecraft/client/model/Model;"
			+ "Ljava/lang/Object;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;"
			+ "III"
			+ "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

	@WrapOperation(method = "submit", at = @At(value = "INVOKE", target = SUBMIT_MODEL))
	private void preciseglower$skipEyes(
		OrderedSubmitNodeCollector collector,
		Model<?> model,
		Object state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		Operation<Void> original
	) {
		if (HiddenAlpha.current() < 1F) return;
		original.call(collector, model, state, poseStack, renderType, lightCoords, overlayCoords, outlineColor, crumblingOverlay);
	}
}
