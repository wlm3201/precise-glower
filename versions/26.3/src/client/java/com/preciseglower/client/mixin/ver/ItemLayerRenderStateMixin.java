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
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3：同 26.1.2，但 {@code BakedQuad.MaterialInfo} 多出了两个 glint 渲染层，
 * 且 shade(boolean) 换成了 shadeDirectionOverride(Direction)。
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemLayerRenderStateMixin {
	private static final String SUBMIT_ITEM =
		"Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem("
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/world/item/ItemDisplayContext;"
			+ "III[I"
			// 26.3 起 quads 参数从 List<BakedQuad> 换成了 ItemQuads
			+ "Lnet/minecraft/client/resources/model/geometry/ItemQuads;"
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
		ItemQuads quads,
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

		List<BakedQuad> modifiedQuads = new ArrayList<>(quads.all().size());
		for (BakedQuad quad : quads.all()) {
			boolean tinted = quad.materialInfo().isTinted();
			modifiedQuads.add(preciseglower$retint(quad, tinted ? -1 : synthetic));
		}
		// 换成了 translucent 的 quad 由 split 归类到半透明组，才能真的参与混合
		original.call(
			collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
			modifiedTints, ItemQuads.split(modifiedQuads), foilType
		);
	}

	@Unique
	private static BakedQuad preciseglower$retint(BakedQuad quad, int tintIndex) {
		BakedQuad.MaterialInfo info = quad.materialInfo();
		int index = tintIndex < 0 ? info.tintIndex() : tintIndex;
		RenderType type = info.itemRenderType();
		if (type == Sheets.cutoutItemSheet()) type = Sheets.translucentItemSheet();
		else if (type == Sheets.cutoutBlockItemSheet()) type = Sheets.translucentBlockItemSheet();
		BakedQuad.MaterialInfo modified = new BakedQuad.MaterialInfo(
			info.sprite(),
			info.layer(),
			type,
			info.itemGlintRenderType(),
			info.itemGlintSpecialRenderType(),
			index,
			info.shadeDirectionOverride(),
			info.lightEmission()
		);
		return new BakedQuad(
			quad.position0(), quad.position1(), quad.position2(), quad.position3(),
			quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
			quad.direction(), modified
		);
	}
}
