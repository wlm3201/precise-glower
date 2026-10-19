package com.preciseglower.client.config;

import com.preciseglower.client.util.NbtMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 实体高亮规则：实体类型（可用 {@code *} 通配）+ 可选 NBT 过滤。
 * 例：{@code *{IsBaby:true}} 高亮所有幼年生物。
 */
public class EntityRule extends Rule {
	public static final String WILDCARD = "*";

	public String type = "minecraft:creeper";
	/** NBT 过滤表达式，可为空 */
	public String nbt = "";

	private transient @Nullable EntityType<?> entityType;
	private transient NbtMatcher matcher = NbtMatcher.ANY;
	private transient String error;
	private transient boolean wildcard;

	@Override
	public String key() {
		return type + "|" + (nbt == null ? "" : nbt.trim());
	}

	@Override
	public String filterText() {
		return nbt == null ? "" : nbt.trim();
	}

	@Override
	public String error() {
		return error;
	}

	@Override
	public boolean isWildcard() {
		return wildcard;
	}

	public @Nullable EntityType<?> entityType() {
		return entityType;
	}

	public NbtMatcher matcher() {
		return matcher;
	}

	/**
	 * 类型维度是否命中。
	 *
	 * <p>通配符 {@code *} 默认<strong>不含掉落物</strong>：掉落物是物品规则的地盘，
	 * 否则一条 {@code *} 会把满地的掉落物一起圈进来。想连掉落物一起匹配就显式写
	 * {@code minecraft:item}（它照样会让位于命中的物品规则）。</p>
	 */
	public boolean matchesType(Entity entity) {
		if (wildcard) return !(entity instanceof ItemEntity);
		return entityType != null && entityType == entity.getType();
	}

	@Override
	public void compile() {
		entityType = null;
		matcher = NbtMatcher.ANY;
		error = null;
		String t = type == null ? "" : type.trim();
		wildcard = WILDCARD.equals(t);
		if (!wildcard) {
			// 存在性按 keySet 判定（getValue 对不存在的 id 也返回非 null）
			Identifier parsed = Identifier.tryParse(t);
			if (parsed == null || !BuiltInRegistries.ENTITY_TYPE.keySet().contains(parsed)) {
				error = tr("precise-glower.error.unknown_entity", t);
			} else {
				entityType = BuiltInRegistries.ENTITY_TYPE.getValue(parsed);
				if (entityType == null) error = tr("precise-glower.error.unknown_entity", t);
			}
		}
		try {
			matcher = NbtMatcher.parse(nbt);
		} catch (Exception e) {
			matcher = NbtMatcher.ANY;
			// id 的错误优先展示，NBT 语法错误只在 id 没问题的时候覆盖
			if (error == null) error = tr("precise-glower.error.nbt");
		}
	}

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	@Override
	public String displayName() {
		String s = type == null ? "" : type.trim();
		if (WILDCARD.equals(s)) return WILDCARD;
		String localized = com.preciseglower.client.gui.autocomplete.NameIndex.entityName(s);
		if (!localized.isEmpty()) return localized;
		int i = s.indexOf(':');
		return i < 0 ? s : s.substring(i + 1);
	}

	@Override
	protected Rule newInstance() {
		return new EntityRule();
	}

	@Override
	protected void copyInto(Rule target) {
		EntityRule t = (EntityRule) target;
		t.type = this.type;
		t.nbt = this.nbt;
	}
}
