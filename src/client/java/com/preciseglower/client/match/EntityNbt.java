package com.preciseglower.client.match;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

/** 把客户端可见的实体 / 物品数据序列化为 {@link CompoundTag}，供 NBT 过滤与补全使用。 */
public final class EntityNbt {
	private EntityNbt() {
	}

	public static @Nullable CompoundTag of(Entity entity, HolderLookup.Provider registries) {
		try {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			entity.saveWithoutId(output);
			return output.buildResult();
		} catch (Throwable t) {
			return null;
		}
	}

	public static @Nullable CompoundTag of(ItemStack stack, HolderLookup.Provider registries) {
		try {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			output.store("item", ItemStack.CODEC, stack);
			return output.buildResult().getCompound("item").orElse(null);
		} catch (Throwable t) {
			return null;
		}
	}
}
