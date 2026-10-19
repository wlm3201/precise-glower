package com.preciseglower.client.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读 {@link RenderType} 私有的管线设置，用来克隆出「我们自己的」半透明层 */
@Mixin(RenderType.class)
public interface RenderTypeAccess {
	@Accessor("state")
	RenderSetup preciseglower$state();
}
