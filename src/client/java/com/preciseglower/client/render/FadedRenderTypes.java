package com.preciseglower.client.render;

import com.preciseglower.client.mixin.RenderTypeAccess;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;

/**
 * 「我们的」半透明方块渲染层：按 alpha 各造一份实例，用 <strong>对象身份</strong> 传递淡化信息。
 *
 * <p>为什么绕这么一圈：方块模型的 quad 颜色只认 submit 里的 {@code tintColor}（原版与 Fabric API
 * 都写死 -1），而装了 Sodium 之后原版 {@code BlockModelFeatureRenderer} 被 FRAPI 的 mesh 路径整个
 * 接管，提交阶段能改的只剩渲染层。于是干脆让「渲染层实例本身」携带 alpha —— 提交时换成
 * {@link #faded(float)} 给的那份，绘制时在 {@code RenderTypeFeatureRenderer#getVertexBuilder}
 * （三家渲染器共用的原版基类方法）里认出它、把 buffer 包一层 {@link AlphaVertexConsumer}。</p>
 *
 * <p>身份判定的好处：不会被共享实例误伤（原版自己的 {@code translucentBlockItemSheet} 原样返回），
 * 也不依赖任何第三方的内部类。</p>
 */
public final class FadedRenderTypes {
	/** alpha(0..255) -> 我们的渲染层；alpha 档位最多 256 个，常驻无妨 */
	private static final Map<Integer, RenderType> BY_ALPHA = new HashMap<>();
	/** 我们的渲染层 -> alpha；只认实例，身份判定 */
	private static final Map<RenderType, Float> ALPHAS = new HashMap<>();

	private FadedRenderTypes() {
	}

	public static RenderType faded(float alpha) {
		int a = Math.round(255 * Math.max(0F, Math.min(1F, alpha)));
		RenderType type = BY_ALPHA.get(a);
		if (type == null) {
			type = RenderType.create("preciseglower_faded_block_" + a, baseSetup());
			BY_ALPHA.put(a, type);
			ALPHAS.put(type, a / 255F);
		}
		return type;
	}

	/** 只认我们造出来的实例；其它渲染层（包括原版同参数的那张）一律返回 1 */
	public static float alphaOf(@Nullable RenderType type) {
		Float alpha = type == null ? null : ALPHAS.get(type);
		return alpha == null ? 1F : alpha;
	}

	/** 借用原版「半透明方块模型」那套管线/纹理设置，只换名字换身份 */
	private static RenderSetup baseSetup() {
		return ((RenderTypeAccess) (Object) Sheets.translucentBlockItemSheet()).preciseglower$state();
	}
}
