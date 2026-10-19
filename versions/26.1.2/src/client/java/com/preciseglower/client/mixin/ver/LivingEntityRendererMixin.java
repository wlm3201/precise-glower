package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.1.2 / 26.2：把被隐藏规则命中但未完全隐藏的实体按指定不透明度渲染。
 *
 * <p>这里只管「把主体的渲染层换成会混合的那一层」，颜色的 alpha 由
 * {@link SubmitNodeCollectionMixin} 统一加。</p>
 *
 * <p>注意别改成把 {@code forceTransparent} 置 true 来走原版那条分支：那样 {@code submit}
 * 里的 {@code baseColor} 会变成原版的 {@code 0x26FFFFFF}（约 15%），再乘上我们的不透明度
 * 就淡得过头了。这里改成「借用」那条分支算出渲染层的取值，本身不参与底色计算，
 * 所以最终 alpha 就是规则里那一个值。</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
	private static final String SUBMIT =
		"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
	private static final String GET_RENDER_TYPE =
		"Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType("
			+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;";

	@WrapOperation(
		method = SUBMIT,
		at = @At(value = "INVOKE", target = GET_RENDER_TYPE),
		require = 0
	)
	private RenderType preciseglower$translucentBody(
		LivingEntityRenderer renderer,
		S state,
		boolean isBodyVisible,
		boolean forceTransparent,
		boolean appearGlowing,
		Operation<RenderType> original
	) {
		if (HiddenAlpha.current() >= 1F) {
			return original.call(renderer, state, isBodyVisible, forceTransparent, appearGlowing);
		}
		// 借原版「强制半透明」分支算出这张贴图对应的半透明层（基类之外还可能有子类覆写的判断），
		// 但 submit 里那份 forceTransparent 仍然是原值 —— baseColor 保持 -1，不额外叠一层淡出
		RenderType translucent = original.call(renderer, state, isBodyVisible, true, appearGlowing);
		return translucent != null
			? translucent
			: original.call(renderer, state, isBodyVisible, forceTransparent, appearGlowing);
	}
}
