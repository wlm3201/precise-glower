package com.preciseglower.client.mixin;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.Rule;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 把发光颜色改成规则指定的颜色（原版从队伍颜色取，这里直接覆盖）。 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void preciseglower$getTeamColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		Rule rule = PreciseGlowerClient.entityTracker().ruleFor(self);
		if (rule != null && rule.glow) {
			cir.setReturnValue(PreciseGlowerClient.entityTracker().colorFor(self, rule) | 0xFF000000);
			cir.cancel();
		}
	}
}
