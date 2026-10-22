package com.preciseglower.client.config;

import com.preciseglower.client.util.NbtMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

public class EntityRule extends Rule {
    public static final String WILDCARD = "*";

    public String type = "minecraft:creeper";

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
            Identifier parsed = Identifier.tryParse(t);
            if (parsed == null || !BuiltInRegistries.ENTITY_TYPE.keySet().contains(parsed)) {
                error = tr("preciseglower.error.unknown_entity", t);
            } else {
                entityType = BuiltInRegistries.ENTITY_TYPE.getValue(parsed);
                if (entityType == null) error = tr("preciseglower.error.unknown_entity", t);
            }
        }
        try {
            matcher = NbtMatcher.parse(nbt);
        } catch (Exception e) {
            matcher = NbtMatcher.ANY;
            if (error == null) error = tr("preciseglower.error.nbt");
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
