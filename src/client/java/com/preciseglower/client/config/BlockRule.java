package com.preciseglower.client.config;

import com.preciseglower.client.gui.autocomplete.IdSuggester;
import com.preciseglower.client.match.BlockStateFilter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class BlockRule extends Rule {
    public static final String WILDCARD = "*";

    public String id = "minecraft:chest";

    private transient @Nullable BlockStateFilter filter;
    private transient String error;
    private transient Block block;

    @Override
    public String key() {
        return id;
    }

    @Override
    public String filterText() {
        int i = id.indexOf('[');
        return i < 0 ? "" : id.substring(i);
    }

    public String blockId() {
        int i = id.indexOf('[');
        return i < 0 ? id.trim() : id.substring(0, i).trim();
    }

    @Override
    public boolean isWildcard() {
        return WILDCARD.equals(blockId());
    }

    public boolean isTag() {
        return blockId().startsWith("#");
    }

    @Override
    public int specificity() {
        if (isWildcard()) return super.specificity();
        if (isTag()) return 1 + (filterText().isBlank() ? 0 : 1);
        return super.specificity();
    }

    @Override
    public String error() {
        return error;
    }

    public @Nullable Block block() {
        return block;
    }

    public @Nullable BlockStateFilter filter() {
        return filter;
    }

    @Override
    public void compile() {
        filter = null;
        block = null;
        error = null;
        if (id == null || id.isBlank()) {
            error = tr("preciseglower.error.empty");
            return;
        }
        String rawId = blockId();
        if (isTag()) {
            if (!BlockStateFilter.tagsAvailable()) {
                error = tr("preciseglower.error.tag_needs_world", rawId);
                return;
            }
            if (!IdSuggester.blockTagExists(rawId)) {
                error = tr("preciseglower.error.unknown_tag", rawId);
                return;
            }
        } else if (!WILDCARD.equals(rawId)) {
            Identifier parsedId = Identifier.tryParse(rawId);
            if (parsedId == null || !BuiltInRegistries.BLOCK.keySet().contains(parsedId)) {
                error = tr("preciseglower.error.unknown_block", rawId);
                return;
            }
        }
        try {
            filter = BlockStateFilter.parse(id);
            if (filter.isAnyBlock() && filter.isAnyState()) {
                filter = null;
                error = tr("preciseglower.error.wildcard_state");
                return;
            }
            block = filter.block();
        } catch (Exception e) {
            error = tr("preciseglower.error.parse");
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
        String s = blockId();
        if (WILDCARD.equals(s)) return WILDCARD;
        if (isTag()) {
            int i = s.indexOf(':');
            return "#" + (i < 0 ? s.substring(1) : s.substring(i + 1));
        }
        String localized = com.preciseglower.client.gui.autocomplete.NameIndex.blockName(s);
        if (!localized.isEmpty()) return localized;
        int i = s.indexOf(':');
        return i < 0 ? s : s.substring(i + 1);
    }

    public String namespace() {
        String s = blockId();
        int i = s.indexOf(':');
        return i < 0 ? "minecraft" : s.substring(0, i);
    }

    public boolean isVanilla() {
        return "minecraft".equals(namespace());
    }

    public String registryName() {
        return BuiltInRegistries.BLOCK.getKey(block()) == null
                ? blockId()
                : String.valueOf(BuiltInRegistries.BLOCK.getKey(block()));
    }

    @Override
    protected Rule newInstance() {
        return new BlockRule();
    }

    @Override
    protected void copyInto(Rule target) {
        ((BlockRule) target).id = this.id;
    }
}
