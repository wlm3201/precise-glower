package com.preciseglower.client.mixin.ver;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.preciseglower.client.render.HiddenAlpha;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 半透明时把「不开混合」的实体渲染层换成对应的半透明层。
 *
 * <p>生物那边有 {@link LivingEntityRendererMixin} 换主体、{@link RenderLayerMixin} 换叠加层、
 * {@link EquipmentLayerRendererMixin} 换盔甲，但船、矿车、箭、末影水晶、唤魔者尖牙、末影龙、
 * 盔甲架这些是不走上面几条路的：它们提交模型时只给一张贴图，渲染层由
 * {@code Model#renderType(texture)} 查出来 —— 也就是这里的 {@code entityCutout} /
 * {@code entitySolid} / {@code entityCutoutCull}……这些层都没有混合，alpha 改了也看不出来。</p>
 *
 * <p>所以干脆在源头换：这些工厂方法在隐藏窗口内一律返回 {@code entityTranslucent}。</p>
 */
@Mixin(RenderTypes.class)
public class RenderTypesMixin {
	private static final String IDENTIFIER =
		"(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;";
	private static final String IDENTIFIER_BOOL =
		"(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;";

	@WrapMethod(method = "entitySolid" + IDENTIFIER)
	private static RenderType preciseglower$solid(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	@WrapMethod(method = "entitySolidZOffsetForward" + IDENTIFIER)
	private static RenderType preciseglower$solidZOffset(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	@WrapMethod(method = "entityCutout" + IDENTIFIER)
	private static RenderType preciseglower$cutout(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	@WrapMethod(method = "entityCutout" + IDENTIFIER_BOOL)
	private static RenderType preciseglower$cutoutAffectsOutline(
		Identifier texture, boolean affectsOutline, Operation<RenderType> original
	) {
		return HiddenAlpha.current() >= 1F ? original.call(texture, affectsOutline) : RenderTypes.entityTranslucent(texture, affectsOutline);
	}

	@WrapMethod(method = "entityCutoutCull" + IDENTIFIER)
	private static RenderType preciseglower$cutoutCull(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	@WrapMethod(method = "entityCutoutZOffset" + IDENTIFIER)
	private static RenderType preciseglower$cutoutZOffset(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	@WrapMethod(method = "entityCutoutZOffset" + IDENTIFIER_BOOL)
	private static RenderType preciseglower$cutoutZOffsetAffectsOutline(
		Identifier texture, boolean affectsOutline, Operation<RenderType> original
	) {
		return HiddenAlpha.current() >= 1F ? original.call(texture, affectsOutline) : RenderTypes.entityTranslucent(texture, affectsOutline);
	}

	/** 眼睛这类叠加层也是没有混合的，跟着一起换，免得实体淡了眼睛还亮着 */
	@WrapMethod(method = "eyes" + IDENTIFIER)
	private static RenderType preciseglower$eyes(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	@WrapMethod(method = "breezeEyes" + IDENTIFIER)
	private static RenderType preciseglower$breezeEyes(Identifier texture, Operation<RenderType> original) {
		return translucent(texture, original);
	}

	private static RenderType translucent(Identifier texture, Operation<RenderType> original) {
		return HiddenAlpha.current() >= 1F ? original.call(texture) : RenderTypes.entityTranslucent(texture);
	}
}
