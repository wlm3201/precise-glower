package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.preciseglower.client.render.BlockFade;
import com.preciseglower.client.render.HiddenAlpha;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 把隐藏规则的 alpha 统一加在「模型提交」的上色参数上。
 *
 * <p>{@code SubmitNodeCollection} 是 {@code OrderedSubmitNodeCollector} 唯一的实现类，
 * 所有 {@code submitModel} —— 包括那些不带颜色参数的重载、以及 {@code SubmitNodeStorage}
 * 转发过来的调用 —— 最后都落在它这一个方法上。所以只在这里改一次颜色，实体主体、盔甲、
 * 以及所有 RenderLayer 叠加层（村民衣服、溺尸外衣、羊毛、马斑点……）就全都跟着变淡了。</p>
 *
 * <p>正因为这里是全包的，其它调用点不要再改一遍颜色，否则 alpha 会被乘两次。</p>
 *
 * <p>另外两处也在这里顺手处理：{@code submitText} 改文字与背景色（文本展示实体），
 * {@code submitMovingBlock} 把落沙这类移动方块登记进 {@link BlockFade}。</p>
 *
 * <p>这里一律用 {@code WrapMethod} 而不是 {@code ModifyVariable}：MC 的类没有局部变量表，
 * 按类型 / 序号找局部变量是找不到的（已实测：注入检查 0/1 通过，游戏直接起不来）。</p>
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@WrapMethod(method = "submitModel")
	private <S> void preciseglower$applyAlpha(
		Model<? super S> model,
		S state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int tintedColor,
		TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		Operation<Void> original
	) {
		original.call(
			model, state, poseStack, renderType, lightCoords, overlayCoords,
			HiddenAlpha.apply(tintedColor, HiddenAlpha.current()), sprite, outlineColor, crumblingOverlay
		);
	}

	/**
	 * 方块模型实体（TNT / TNT 矿车 / 方块展示）默认走 {@code cutoutBlockItemSheet}（没开混合）。
	 * 换成 {@code translucentBlockItemSheet} 后，它同时会被路由进半透明队列 —— 这正是
	 * 原版处理「本身带透明贴图的方块模型」时用的那一层。
	 */
	/** 文本展示实体：文字色与背景色一起压 alpha */
	@WrapMethod(method = "submitText")
	private void preciseglower$textAlpha(
		PoseStack poseStack,
		float x,
		float y,
		FormattedCharSequence string,
		boolean dropShadow,
		Font.DisplayMode displayMode,
		int lightCoords,
		int color,
		int backgroundColor,
		int outlineColor,
		Operation<Void> original
	) {
		float alpha = HiddenAlpha.current();
		original.call(
			poseStack, x, y, string, dropShadow, displayMode, lightCoords,
			HiddenAlpha.apply(color, alpha), HiddenAlpha.apply(backgroundColor, alpha), outlineColor
		);
	}

	/**
	 * 移动方块（落沙 / 砂砾 / 铁砧）：把 {@code MovingBlockRenderState} 实例登记进
	 * {@link BlockFade}，绘制阶段（{@code MovingBlockFeatureRendererMixin}）按它取回 alpha。
	 */
	/** 26.1.2：{@code submitMovingBlock} 没有末尾的 outlineColor 参数 */
	@WrapMethod(method = "submitMovingBlock")
	private void preciseglower$markMovingBlock(
		PoseStack poseStack,
		net.minecraft.client.renderer.block.MovingBlockRenderState movingBlockRenderState,
		Operation<Void> original
	) {
		BlockFade.markMoving(movingBlockRenderState, HiddenAlpha.current());
		original.call(poseStack, movingBlockRenderState);
	}
}
