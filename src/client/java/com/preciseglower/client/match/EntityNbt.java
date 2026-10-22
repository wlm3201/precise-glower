package com.preciseglower.client.match;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

public final class EntityNbt {
    private EntityNbt() {}

    public static @Nullable CompoundTag of(Entity entity, HolderLookup.Provider registries) {
        try {
            TagValueOutput output =
                    TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            entity.saveWithoutId(output);
            return output.buildResult();
        } catch (Throwable t) {
            return null;
        }
    }

    public static @Nullable CompoundTag of(ItemStack stack, HolderLookup.Provider registries) {
        try {
            TagValueOutput output =
                    TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            output.store("item", ItemStack.CODEC, stack);
            return output.buildResult().getCompound("item").orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
