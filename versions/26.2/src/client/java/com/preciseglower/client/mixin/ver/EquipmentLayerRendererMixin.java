package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.1.2 / 26.2：让盔甲 / 鞘翅跟着隐藏规则的不透明度一起变淡。
 *
 * <p>盔甲走的是 {@code RenderTypes.armorCutoutNoCull}（只有 alpha 测试、没开混合，颜色里的 alpha 会被丢掉），
 * 所以每个 draw call 都得两步：先换成半透明渲染层，再改提交模型的颜色 alpha。</p>
 *
 * <p>注意注入目标里的 owner：这里是在 {@code submitNodeCollector.order(...)} 的返回值上再
 * {@code submitModel}，字节码里的 owner 是 {@code OrderedSubmitNodeCollector} 而不是
 * {@code SubmitNodeCollector}（对照 {@code LivingEntityRenderer#submit}，那边才是后者）。
 * Mixin 的 owner 是精确匹配的，写错就是一个点都匹不上。</p>
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
	private static final String SUBMIT_MODEL =
		"Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
			+ "Lnet/minecraft/client/model/Model;"
			+ "Ljava/lang/Object;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;"
			+ "III"
			+ "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
			+ "I"
			+ "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";
	private static final String ARMOR_TRIMS =
		"Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)"
			+ "Lnet/minecraft/client/renderer/rendertype/RenderType;";

	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = ARMOR_CUTOUT)
	)
	private RenderType preciseglower$translucentArmor(Identifier texture, Operation<RenderType> original) {
		if (HiddenAlpha.current() >= 1F) return original.call(texture);
		return RenderTypes.armorTranslucent(texture);
	}

	/** 盔甲描边：原版用的是同一张贴图上的裁剪层（也没有混合），换成半透明层才会跟着淡出 */
	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = ARMOR_TRIMS)
	)
	private RenderType preciseglower$translucentTrims(boolean decal, Operation<RenderType> original) {
		if (HiddenAlpha.current() >= 1F) return original.call(decal);
		return RenderTypes.armorTranslucent(Sheets.ARMOR_TRIMS_SHEET);
	}

	@WrapOperation(
		method = RENDER_LAYERS_DETAIL,
		at = @At(value = "INVOKE", target = SUBMIT_MODEL)
	)
	private void preciseglower$skipGlint(
		OrderedSubmitNodeCollector collector,
		Model<?> model,
		Object state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int color,
		TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		Operation<Void> original
	) {
		// 颜色的 alpha 由 SubmitNodeCollectionMixin 统一加，这里只管处理「加了也没用」的那部分：
		// 附魔闪光走 GLINT 管线，顶点格式只有 POSITION_TEX（不带颜色分量），alpha 传不进去 —— 半透明时直接省掉
		if (HiddenAlpha.current() < 1F && renderType.equals(RenderTypes.armorEntityGlint())) return;
		original.call(
			collector, model, state, poseStack, renderType, lightCoords, overlayCoords,
			color, sprite, outlineColor, crumblingOverlay
		);
	}
}
