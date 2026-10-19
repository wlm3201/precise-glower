package com.preciseglower.client.mixin;

import com.preciseglower.client.PreciseGlowerClient;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 准心过滤：被隐藏规则命中的实体不参与准心命中判定。
 * <p>
 * 26.x 的准心命中在 {@code LocalPlayer#raycastHitResult} / {@code LocalPlayer#pick} 里做，
 * 这里不改动命中结果本身，只把传给 {@code ProjectileUtil} / {@code AttackRange} 的
 * {@code Predicate<Entity>} 包一层，等价于给原版的选择器再加一个条件。
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	private static final String PICK =
		"pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;";
	private static final String RAYCAST =
		"raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;";
	private static final String GET_ENTITY_HIT_RESULT =
		"Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult("
			+ "Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/world/phys/Vec3;"
			+ "Lnet/minecraft/world/phys/Vec3;"
			+ "Lnet/minecraft/world/phys/AABB;"
			+ "Ljava/util/function/Predicate;D)"
			+ "Lnet/minecraft/world/phys/EntityHitResult;";
	private static final String GET_CLOSEST_HIT =
		"Lnet/minecraft/world/item/component/AttackRange;getClosesetHit("
			+ "Lnet/minecraft/world/entity/Entity;F"
			+ "Ljava/util/function/Predicate;)"
			+ "Lnet/minecraft/world/phys/HitResult;";

	/** 普通准心命中：ProjectileUtil 的第 5 个参数是实体筛选谓词 */
	@ModifyArg(method = PICK, at = @At(value = "INVOKE", target = GET_ENTITY_HIT_RESULT), index = 4, require = 0)
	private static Predicate<Entity> preciseglower$filterPickPredicate(Predicate<Entity> original) {
		return wrap(original);
	}

	/** 带攻击距离加成的那条分支（AttackRange#getClosesetHit） */
	@ModifyArg(method = RAYCAST, at = @At(value = "INVOKE", target = GET_CLOSEST_HIT), index = 2, require = 0)
	private Predicate<Entity> preciseglower$filterClosestHitPredicate(Predicate<Entity> original) {
		return wrap(original);
	}

	private static Predicate<Entity> wrap(Predicate<Entity> original) {
		if (original == null) return original;
		// 完全隐藏的一定不可选中；半透明的那部分看规则上的「忽略交互」
		return entity -> original.test(entity) && !PreciseGlowerClient.hideTracker().skipsInteraction(entity);
	}
}
