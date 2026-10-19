package com.preciseglower.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.preciseglower.client.render.HiddenAlpha;
import com.preciseglower.client.render.HideStateHook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 实体 submit 阶段只能拿到 render state，拿不到实体对象，所以在这里把「这条 state 对应
 * 的不透明度」取出来放进线程局部变量，供 {@code ver.LivingEntityRendererMixin} 使用。
 */
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
		Operation<Void> original
	) {
		HiddenAlpha.begin(renderState instanceof HideStateHook hook ? hook.preciseglower$getAlpha() : 1F);
		try {
			original.call(renderState, camera, x, y, z, poseStack, submitNodeCollector);
		} finally {
			HiddenAlpha.end();
		}
	}
}
