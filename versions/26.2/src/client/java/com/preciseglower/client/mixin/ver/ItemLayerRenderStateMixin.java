package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.preciseglower.client.render.HiddenAlpha;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.1.2 / 26.2：让手持 / 头上的物品跟着隐藏规则的不透明度变淡。
 *
 * <p>物品的着色只走「tint 层」：有 tintIndex 的 quad 取 {@code tintLayers[tintIndex]}，
 * 没有的取……什么都不取（这一版的 submitItem 只传 outlineColor，没有基础色），
 * 所以要给未着色的 quad 额外造一个「白色 + 我们的 alpha」的 tint 层，并把它们指过去。
 * 同时把 cutout 的物品渲染层换成 translucent，否则 alpha 又是被丢掉的。</p>
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemLayerRenderStateMixin {
	private static final String SUBMIT_ITEM =
		"Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem("
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/world/item/ItemDisplayContext;"
			+ "III[ILjava/util/List;"
			+ "Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V";

	@WrapOperation(method = "submit", at = @At(value = "INVOKE", target = SUBMIT_ITEM), require = 0)
	private void preciseglower$itemAlpha(
		SubmitNodeCollector collector,
		PoseStack poseStack,
		ItemDisplayContext displayContext,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		int[] tints,
		List<BakedQuad> quads,
		ItemStackRenderState.FoilType foilType,
		Operation<Void> original
	) {
		float alpha = HiddenAlpha.current();
		if (alpha >= 1F) {
			original.call(collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tints, quads, foilType);
			return;
		}

		int synthetic = tints.length;
		int[] modifiedTints = new int[synthetic + 1];
		for (int i = 0; i < tints.length; i++) modifiedTints[i] = HiddenAlpha.apply(tints[i], alpha);
		modifiedTints[synthetic] = HiddenAlpha.apply(0xFFFFFFFF, alpha);

		List<BakedQuad> modifiedQuads = new ArrayList<>(quads.size());
		for (BakedQuad quad : quads) {
			boolean tinted = quad.materialInfo().isTinted();
			modifiedQuads.add(preciseglower$retint(quad, tinted ? -1 : synthetic));
		}
		original.call(
			collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
			modifiedTints, modifiedQuads, foilType
		);
	}

	@Unique
	private static BakedQuad preciseglower$retint(BakedQuad quad, int tintIndex) {
		BakedQuad.MaterialInfo info = quad.materialInfo();
		int index = tintIndex < 0 ? info.tintIndex() : tintIndex;
		RenderType type = info.itemRenderType();
		if (type == Sheets.cutoutItemSheet()) type = Sheets.translucentItemSheet();
		else if (type == Sheets.cutoutBlockItemSheet()) type = Sheets.translucentBlockItemSheet();
		BakedQuad.MaterialInfo modified =
			new BakedQuad.MaterialInfo(info.sprite(), info.layer(), type, index, info.shade(), info.lightEmission());
		return new BakedQuad(
			quad.position0(), quad.position1(), quad.position2(), quad.position3(),
			quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
			quad.direction(), modified
		);
	}
}
