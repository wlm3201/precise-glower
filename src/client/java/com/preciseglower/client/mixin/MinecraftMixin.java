package com.preciseglower.client.mixin;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.Rule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让命中的实体/掉落物进入原版发光渲染管线。 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void preciseglower$shouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Rule rule = PreciseGlowerClient.entityTracker().ruleFor(entity);
		if (rule != null && rule.glow) {
			cir.setReturnValue(true);
			cir.cancel();
		}
	}
}
