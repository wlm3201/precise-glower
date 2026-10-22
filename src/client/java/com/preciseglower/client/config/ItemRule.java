package com.preciseglower.client.config;

import com.preciseglower.client.util.NbtMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class ItemRule extends Rule {
    public static final String WILDCARD = "*";

    public String item = "minecraft:diamond";

    public String nbt = "";

    public int minCount = 1;

    private transient @Nullable Item itemType;
    private transient NbtMatcher matcher = NbtMatcher.ANY;
    private transient String error;
    private transient boolean wildcard;

    @Override
    public String key() {
        return item + "|" + (nbt == null ? "" : nbt.trim());
    }

    @Override
    public String filterText() {
        return nbt == null ? "" : nbt.trim();
    }

    public int minCount() {
        return minCount < 1 ? 1 : minCount;
    }

    @Override
    public String filterSummary() {
        return minCount() > 1 ? "≥" + minCount() : super.filterSummary();
    }

    @Override
    public int specificity() {
        return super.specificity() + (minCount() > 1 ? 1 : 0);
    }

    @Override
    public String error() {
        return error;
    }

    @Override
    public boolean isWildcard() {
        return wildcard;
    }

    public @Nullable Item itemType() {
        return itemType;
    }

    public NbtMatcher matcher() {
        return matcher;
    }

    @Override
    public void compile() {
        itemType = null;
        matcher = NbtMatcher.ANY;
        error = null;
        String t = item == null ? "" : item.trim();
        wildcard = WILDCARD.equals(t);
        if (!wildcard) {
            Identifier parsed = Identifier.tryParse(t);
            if (parsed == null || !BuiltInRegistries.ITEM.keySet().contains(parsed)) {
                error = tr("preciseglower.error.unknown_item", t);
            } else {
                itemType = BuiltInRegistries.ITEM.getValue(parsed);
                if (itemType == null) error = tr("preciseglower.error.unknown_item", t);
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
        String s = item == null ? "" : item.trim();
        if (WILDCARD.equals(s)) return WILDCARD;
        String localized = com.preciseglower.client.gui.autocomplete.NameIndex.itemName(s);
        if (!localized.isEmpty()) return localized;
        int i = s.indexOf(':');
        return i < 0 ? s : s.substring(i + 1);
    }

    @Override
    protected Rule newInstance() {
        return new ItemRule();
    }

    @Override
    protected void copyInto(Rule target) {
        ItemRule t = (ItemRule) target;
        t.item = this.item;
        t.nbt = this.nbt;
        t.minCount = this.minCount;
    }
}
