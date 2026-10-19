package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 移动方块（落沙 / 砂砾 / 铁砧）淡出的关键一步：把它塞进半透明相位。
 *
 * <p>原版在这里用 {@code model.hasMaterialFlag(FLAG_TRANSLUCENT)} 决定进哪个相位：
 * 有半透明材质的进 {@code translucentBlocksAndItems}（按距离排序），其余进 {@code solid}
 * （先画、不排序）。我们只是把不透明的沙子改成半透明，它的模型并没有那个标志位，于是留在 solid
 * 相位抢先画完并写上深度 —— 身后没淡化的东西就被深度测试挡掉了，表现为「有的朝向才透得过去」。</p>
 *
 * <p>所以这里在半透明时谎报一次标志位，让它进排序相位；alpha 本身由
 * {@link MovingBlockFeatureRendererMixin} 在画 quad 时乘上去。</p>
 */
@Mixin(SubmitNodeCollection.class)
public class MovingBlockPhaseMixin {
	private static final String SUBMIT_MOVING_BLOCK =
		"submitMovingBlock(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/block/MovingBlockRenderState;I)V";
	private static final String HAS_MATERIAL_FLAG =
		"Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z";

	@WrapOperation(
		method = SUBMIT_MOVING_BLOCK,
		at = @At(value = "INVOKE", target = HAS_MATERIAL_FLAG)
	)
	private boolean preciseglower$forceTranslucentPhase(BlockStateModel model, int flag, Operation<Boolean> original) {
		boolean translucent = original.call(model, flag);
		if (translucent || HiddenAlpha.current() >= 1F) return translucent;
		return flag == BakedQuad.FLAG_TRANSLUCENT;
	}
}
