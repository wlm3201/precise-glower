package com.preciseglower.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.preciseglower.client.render.FadedRenderTypes;
import com.preciseglower.client.render.HiddenAlpha;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 方块模型实体淡出的第 1 步（提交阶段）。
 *
 * <p>TNT、方块展示、矿车里装的方块这类实体都是拿 {@link BlockModelRenderState} 提交的，
 * 默认渲染层是 {@code cutoutBlockItemSheet}（没开混合，alpha 改了也看不出来）。这里在半透明时
 * 把它换成 {@link FadedRenderTypes#faded} 里「带 alpha 身份」的那份 —— 它借用了原版半透明方块模型
 * 的整套管线设置，所以照样会混合、照样被路由进半透明队列；绘制阶段由
 * {@code RenderTypeFeatureRendererMixin} 认出它并包一层强改 alpha 的 buffer。</p>
 *
 * <p>注：装了 Sodium 后这条路径会走 Fabric 的 mesh 分支（{@code submitModel} 被 FRAPI 注入取消），
 * 但我们的包装在最外层、改的是入参，两条分支都吃得到。</p>
 */
@Mixin(BlockModelRenderState.class)
public abstract class BlockModelRenderStateMixin {
	private static final String SUBMIT_MODEL =
		"submitModel(Lnet/minecraft/client/renderer/rendertype/RenderType;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

	@WrapMethod(method = SUBMIT_MODEL)
	private void preciseglower$fadeBlockModel(
		RenderType renderType,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int externalLightCoords,
		int overlayCoords,
		int outlineColor,
		Operation<Void> original
	) {
		float alpha = HiddenAlpha.current();
		if (alpha >= 1F) {
			original.call(renderType, poseStack, submitNodeCollector, externalLightCoords, overlayCoords, outlineColor);
			return;
		}
		original.call(
			FadedRenderTypes.faded(alpha), poseStack, submitNodeCollector, externalLightCoords, overlayCoords, outlineColor
		);
	}
}
