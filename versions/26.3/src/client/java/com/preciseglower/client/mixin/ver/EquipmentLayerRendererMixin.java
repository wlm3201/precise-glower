package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3：同 26.1.2，但 {@code submitModel} 末尾的 CrumblingOverlay 被去掉了，
 * 且 {@code armorTranslucent} 也不存在了 —— 半透明盔甲改用 {@code entityTranslucent}。
 *
 * <p>注意注入目标里的 owner：这里是在 {@code submitNodeCollector.order(...)} 的返回值上再
 * {@code submitModel}，字节码里的 owner 是 {@code OrderedSubmitNodeCollector} 而不是
 * {@code SubmitNodeCollector}。Mixin 的 owner 是精确匹配的，写错就是一个点都匹不上。</p>
 */
@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
	private static final String RENDER_LAYERS_DETAIL =
		"renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
			+ "Lnet/minecraft/resources/ResourceKey;"
			+ "Lnet/minecraft/client/model/Model;"
			+ "Ljava/lang/Object;"
			+ "Lnet/minecraft/world/item/ItemStack;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "I"
			+ "Lnet/minecraft/resources/Identifier;"
			+ "II)V";
	private static final String ARMOR_CUTOUT =
		"Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull("
			+ "Lnet/minecraft/resources/Identifier;)"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;";
	// 26.3 里带附魔的盔甲走 armorCutoutNoCullGlint（单一的盔甲+附魔合成绘制），它的片元着色器会把 alpha 抬回去，
	// 半透明化无效。这种描边直接换成普通的半透明实体层：牺牲附魔闪光，换取正确的淡出。
	private static final String ARMOR_CUTOUT_GLINT =
		"Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint("
			+ "Lnet/minecraft/resources/Identifier;)"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;";
	private static final String SUBMIT_MODEL =
		"Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
			+ "Lnet/minecraft/client/model/Model;"
			+ "Ljava/lang/Object;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;"
			+ "III"
			+ "Lnet/minecraft/client/renderer/texture/UvMapping;"
			+ "I)V";
	private static final String ARMOR_TRIM =
		"Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim("
			+ "Lnet/minecraft/resources/Identifier;Z)"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;";

	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = ARMOR_CUTOUT)
	)
	private RenderType preciseglower$translucentArmor(Identifier texture, Operation<RenderType> original) {
		if (HiddenAlpha.current() >= 1F) return original.call(texture);
		return RenderTypes.entityTranslucent(texture);
	}

	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = ARMOR_CUTOUT_GLINT)
	)
	private RenderType preciseglower$translucentArmorGlint(Identifier texture, Operation<RenderType> original) {
		if (HiddenAlpha.current() >= 1F) return original.call(texture);
		return RenderTypes.entityTranslucent(texture);
	}

	/** 盔甲描边：原版用的是带 alpha 测试的 {@code armorTrim}，也没开混合 —— 换成半透明层才会跟着淡出 */
	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = ARMOR_TRIM)
	)
	private RenderType preciseglower$translucentTrim(Identifier texture, boolean decal, Operation<RenderType> original) {
		if (HiddenAlpha.current() >= 1F) return original.call(texture, decal);
		return RenderTypes.entityTranslucent(texture);
	}

	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = SUBMIT_MODEL)
	)
	private void preciseglower$skipTrimGlint(
		OrderedSubmitNodeCollector collector,
		Model<?> model,
		Object state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int color,
		UvMapping uvMapping,
		int outlineColor,
		Operation<Void> original
	) {
		// 颜色的 alpha 由 SubmitNodeCollectionMixin 统一加，这里只管处理「加了也没用」的那部分：
		// 描边专属的附魔闪光走 GLINT 管线，顶点格式不带颜色分量，alpha 传不进去 —— 半透明时直接省掉
		if (HiddenAlpha.current() < 1F && renderType.equals(RenderTypes.trimmedArmorGlint())) return;
		original.call(
			collector, model, state, poseStack, renderType, lightCoords, overlayCoords,
			color, uvMapping, outlineColor
		);
	}
}
