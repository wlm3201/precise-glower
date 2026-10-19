package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.preciseglower.client.render.AlphaVertexConsumer;
import com.preciseglower.client.render.BlockFade;
import com.preciseglower.client.render.FadedRenderTypes;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 方块模型实体淡出的第 2 步（绘制阶段）。
 *
 * <p>{@code getVertexBuilder} 是原版 {@link RenderTypeFeatureRenderer} 的方法，而原版
 * {@code BlockModelFeatureRenderer}、Fabric Indigo、Sodium 三家的方块模型渲染器全都继承它、
 * 全都从这里拿 buffer —— 所以只需要在返回值上认一下「是不是我们的淡化层」，是就包一层
 * {@link AlphaVertexConsumer}，不管哪家、不管走 putQuad 还是 mesh 的 bufferQuad 都覆盖。</p>
 *
 * <p>identity 判定保证了零误伤：原版自己的 {@code translucentBlockItemSheet} 是另一个实例。</p>
 */
@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
	@WrapMethod(method = "getVertexBuilder")
	protected VertexConsumer preciseglower$wrapFaded(RenderType renderType, Operation<VertexConsumer> original) {
		// 方块模型：渲染层实例本身带着 alpha（提交阶段烘进去的），它已经是会混合的那张
		float alpha = FadedRenderTypes.alphaOf(renderType);
		if (alpha >= 1F) {
			// 移动方块（落沙等）：渲染层是绘制期按 quad 的材质层现选的，沙子是 SOLID ->
			// solidMovingBlock 不开混合，alpha 写了也白写 —— 这里一并换成会混合的那张
			alpha = BlockFade.current();
			if (alpha < 1F) renderType = RenderTypes.translucentMovingBlock();
		}
		VertexConsumer buffer = original.call(renderType);
		return alpha < 1F ? new AlphaVertexConsumer(buffer, alpha) : buffer;
	}
}
