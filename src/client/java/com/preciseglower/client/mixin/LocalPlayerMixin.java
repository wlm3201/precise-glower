package com.preciseglower.client.mixin;

import com.preciseglower.client.PreciseGlowerClient;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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

    @ModifyArg(
            method = PICK,
            at = @At(value = "INVOKE", target = GET_ENTITY_HIT_RESULT),
            index = 4,
            require = 0)
    private static Predicate<Entity> preciseglower$filterPickPredicate(Predicate<Entity> original) {
        return wrap(original);
    }

    @ModifyArg(
            method = RAYCAST,
            at = @At(value = "INVOKE", target = GET_CLOSEST_HIT),
            index = 2,
            require = 0)
    private Predicate<Entity> preciseglower$filterClosestHitPredicate(Predicate<Entity> original) {
        return wrap(original);
    }

    private static Predicate<Entity> wrap(Predicate<Entity> original) {
        if (original == null) return original;
        return entity ->
                original.test(entity)
                        && !PreciseGlowerClient.hideTracker().skipsInteraction(entity);
    }
}
