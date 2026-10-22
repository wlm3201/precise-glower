package com.preciseglower.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.BlockMode;
import com.preciseglower.client.config.BlockRule;
import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.GlowerConfig;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.gui.Graphics;
import com.preciseglower.client.gui.UiTheme;
import com.preciseglower.client.gui.autocomplete.BrigadierSuggestions;
import com.preciseglower.client.gui.autocomplete.Candidate;
import com.preciseglower.client.gui.autocomplete.IdSuggester;
import com.preciseglower.client.gui.autocomplete.NbtSamples;
import com.preciseglower.client.gui.autocomplete.NbtSuggester;
import com.preciseglower.client.gui.autocomplete.Suggestion;
import com.preciseglower.client.match.BlockStateFilter;
import com.preciseglower.client.match.PropertyIndex;
import com.preciseglower.client.util.Colors;
import com.preciseglower.client.util.NbtMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public final class RuleEditModal {
    public enum Kind {
        BLOCK,
        ENTITY,
        ITEM,
        HIDE_ENTITY,
        HIDE_ITEM
    }

    private boolean styled() {
        return kind == Kind.BLOCK || kind == Kind.ENTITY || kind == Kind.ITEM;
    }

    public interface Host {
        void requestFocus(AbstractWidget widget);

        int screenWidth();

        int screenHeight();

        Font font();
    }

    private static final int CLOSE_W = 16;

    private final Host host;
    private final Kind kind;
    private final Rule target;
    private final Consumer<Boolean> onClose;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private final List<AbstractWidget> children = new ArrayList<>();
    private final List<EditBox> editBoxes = new ArrayList<>();

    private AbstractWidget draggingChild;

    private SuggestionEditBox idBox;
    private SuggestionEditBox filterBox;
    private ColorPicker colorPicker;
    private Slider redSlider;
    private Slider greenSlider;
    private Slider blueSlider;
    private Slider alphaSlider;

    private Slider opacitySlider;

    private Slider widthSlider;
    private Slider rangeSlider;
    private FlatButton cancelButton;
    private FlatButton saveButton;

    private String statusText = "";
    private boolean statusOk = true;

    private int statusY;

    public RuleEditModal(Host host, Kind kind, Rule target, Consumer<Boolean> onClose) {
        this.host = host;
        this.kind = kind;
        this.target = target;
        this.onClose = onClose;

        this.width = UiTheme.MODAL_W;
        this.height = computeHeight();
        this.x = (host.screenWidth() - width) / 2;
        this.y = Math.max(6, (host.screenHeight() - height) / 2);

        build();
    }

    private boolean hasFilterBox() {
        return kind != Kind.HIDE_ITEM;
    }

    private boolean isCountBox() {
        return kind == Kind.ITEM;
    }

    private String countValue() {
        int min = target instanceof ItemRule itemRule ? itemRule.minCount() : 1;
        return min > 1 ? String.valueOf(min) : "";
    }

    private static OptionalInt parseCount(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return OptionalInt.of(1);
        try {
            int value = Integer.parseInt(text);
            return value >= 1 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static final int TIGHT_GAP = 2;

    private static final int ROW_GAP = 4;

    private static final int STATUS_H = 12;

    private int computeHeight() {
        int h = UiTheme.MODAL_HEAD_H + 4;
        h += UiTheme.CONTROL_H + TIGHT_GAP;
        if (hasFilterBox()) h += UiTheme.CONTROL_H + TIGHT_GAP;
        h += STATUS_H + ROW_GAP;
        if (styled()) {
            h += ColorPicker.preferredHeight() + ROW_GAP;
            h += 4 * UiTheme.SLIDER_ROW_H + ROW_GAP;
            h += UiTheme.SLIDER_ROW_H + ROW_GAP;
            h += UiTheme.SLIDER_ROW_H + ROW_GAP;
        } else if (kind == Kind.HIDE_ENTITY) {
            h += UiTheme.SLIDER_ROW_H + ROW_GAP;
        }
        h += (toggleCount() - 1) * UiTheme.TOGGLE_H;
        h += Math.max(UiTheme.TOGGLE_H, UiTheme.MODAL_BTN_H);
        return h + UiTheme.MODAL_PAD;
    }

    private int toggleCount() {
        if (styled()) {
            return 3;
        }
        return kind == Kind.HIDE_ENTITY ? 2 : 1;
    }

    private void build() {
        Font font = host.font();
        int left = x + UiTheme.MODAL_PAD;
        int innerW = width - UiTheme.MODAL_PAD * 2;
        int cy = y + UiTheme.MODAL_HEAD_H + 4;

        idBox =
                new SuggestionEditBox(
                        font,
                        left,
                        cy,
                        innerW,
                        UiTheme.CONTROL_H,
                        Component.translatable("preciseglower.edit.id"),
                        this::suggestId);
        idBox.setMaxLength(256);
        idBox.setValue(idValue());
        idBox.setHint(
                Component.translatable(
                        kind == Kind.BLOCK
                                ? "preciseglower.hint.block_id"
                                : kind == Kind.ENTITY || kind == Kind.HIDE_ENTITY
                                        ? "preciseglower.hint.entity_id"
                                        : "preciseglower.hint.item_id"));
        idBox.setResponder(
                s -> {
                    statusText = "";
                    validate();
                });
        children.add(idBox);
        editBoxes.add(idBox);
        cy += UiTheme.CONTROL_H + TIGHT_GAP;

        if (hasFilterBox()) {
            String labelKey;
            String hintKey;
            if (isCountBox()) {
                labelKey = "preciseglower.edit.count";
                hintKey = "preciseglower.hint.count";
            } else if (kind == Kind.BLOCK) {
                labelKey = "preciseglower.edit.state";
                hintKey = "preciseglower.hint.state";
            } else {
                labelKey = "preciseglower.edit.nbt";
                hintKey = "preciseglower.hint.nbt";
            }
            Component filterLabel = Component.translatable(labelKey);
            filterBox =
                    new SuggestionEditBox(
                            font,
                            left,
                            cy,
                            innerW,
                            UiTheme.CONTROL_H,
                            filterLabel,
                            isCountBox() ? null : this::suggestFilter);
            filterBox.setMaxLength(isCountBox() ? 8 : 512);
            filterBox.setValue(isCountBox() ? countValue() : target.filterText());
            filterBox.setHint(Component.translatable(hintKey));
            filterBox.setResponder(
                    s -> {
                        statusText = "";
                        validate();
                    });
            children.add(filterBox);
            editBoxes.add(filterBox);
            cy += UiTheme.CONTROL_H + TIGHT_GAP;
        }

        statusY = cy;
        cy += STATUS_H + ROW_GAP;

        if (!styled()) {
            if (kind == Kind.HIDE_ENTITY) {
                opacitySlider =
                        new Slider(
                                left,
                                cy,
                                innerW,
                                Component.translatable("preciseglower.edit.opacity"),
                                0,
                                100,
                                target.opacity,
                                "%.0f",
                                v -> target.opacity = Math.round(v));
                children.add(opacitySlider);
                cy += UiTheme.SLIDER_ROW_H + ROW_GAP;
            }
        } else {
            colorPicker =
                    new ColorPicker(
                            left,
                            cy,
                            innerW,
                            target.color,
                            c -> {
                                target.color = c;
                                syncColorSliders();
                            });
            children.add(colorPicker);
            editBoxes.add(colorPicker.hexBox());
            cy += colorPicker.getHeight() + ROW_GAP;

            int rgbLabelW = 18;
            redSlider =
                    new Slider(
                            left,
                            cy,
                            innerW,
                            Component.literal("R"),
                            0,
                            255,
                            Colors.red(target.color),
                            "%.0f",
                            v -> setChannel(0, v.intValue()),
                            rgbLabelW,
                            UiTheme.DANGER_SOLID,
                            UiTheme.DANGER_SOLID);
            cy += UiTheme.SLIDER_ROW_H;
            greenSlider =
                    new Slider(
                            left,
                            cy,
                            innerW,
                            Component.literal("G"),
                            0,
                            255,
                            Colors.green(target.color),
                            "%.0f",
                            v -> setChannel(1, v.intValue()),
                            rgbLabelW,
                            UiTheme.CHANNEL_G,
                            UiTheme.CHANNEL_G);
            cy += UiTheme.SLIDER_ROW_H;
            blueSlider =
                    new Slider(
                            left,
                            cy,
                            innerW,
                            Component.literal("B"),
                            0,
                            255,
                            Colors.blue(target.color),
                            "%.0f",
                            v -> setChannel(2, v.intValue()),
                            rgbLabelW,
                            UiTheme.CHANNEL_B,
                            UiTheme.CHANNEL_B);
            cy += UiTheme.SLIDER_ROW_H;
            alphaSlider =
                    new Slider(
                            left,
                            cy,
                            innerW,
                            Component.literal("A"),
                            0,
                            255,
                            Colors.alpha(target.color),
                            "%.0f",
                            v -> {
                                target.color = Colors.withAlpha(target.color, v.intValue());
                                colorPicker.setColor(target.color);
                            },
                            rgbLabelW,
                            UiTheme.WHITE,
                            UiTheme.WHITE);
            children.add(redSlider);
            children.add(greenSlider);
            children.add(blueSlider);
            children.add(alphaSlider);
            cy += UiTheme.SLIDER_ROW_H + ROW_GAP;

            widthSlider =
                    new Slider(
                            left,
                            cy,
                            innerW,
                            Component.translatable("preciseglower.edit.width"),
                            0.5F,
                            8.0F,
                            target.lineWidth,
                            "%.1f",
                            v -> target.lineWidth = v);
            children.add(widthSlider);
            cy += UiTheme.SLIDER_ROW_H + ROW_GAP;

            rangeSlider =
                    new Slider(
                            left,
                            cy,
                            innerW,
                            rangeLabel(),
                            Rule.MIN_RANGE_CHUNKS,
                            Rule.MAX_RANGE_CHUNKS,
                            target.rangeChunks(),
                            "%.0f",
                            v -> target.setRangeChunks(Math.round(v)));
            children.add(rangeSlider);
            cy += UiTheme.SLIDER_ROW_H + ROW_GAP;

            if (kind == Kind.BLOCK) {
                addToggle(
                        left,
                        cy,
                        innerW,
                        "preciseglower.edit.wireframe",
                        target.blockMode().wireframe(),
                        v -> target.blockMode = BlockMode.of(v, target.blockMode().outline()));
                cy += UiTheme.TOGGLE_H;
                addToggle(
                        left,
                        cy,
                        innerW,
                        "preciseglower.edit.outline",
                        target.blockMode().outline(),
                        v -> target.blockMode = BlockMode.of(target.blockMode().wireframe(), v));
                cy += UiTheme.TOGGLE_H;
            } else {
                addToggle(
                        left,
                        cy,
                        innerW,
                        "preciseglower.edit.outline",
                        target.glow,
                        v -> target.glow = v);
                cy += UiTheme.TOGGLE_H;
                addToggle(
                        left,
                        cy,
                        innerW,
                        "preciseglower.edit.wireframe",
                        target.showBox,
                        v -> target.showBox = v);
                cy += UiTheme.TOGGLE_H;
            }
        }

        if (kind == Kind.HIDE_ENTITY) {
            addToggle(
                    left,
                    cy,
                    innerW,
                    "preciseglower.edit.ignore_interaction",
                    target.ignoreInteraction,
                    v -> target.ignoreInteraction = v);
            cy += UiTheme.TOGGLE_H;
        }

        int toggleRowY = cy;
        addToggle(
                left,
                toggleRowY,
                innerW,
                "preciseglower.edit.enabled",
                target.enabled,
                v -> target.enabled = v);
        int buttonY = toggleRowY + (UiTheme.TOGGLE_H - UiTheme.MODAL_BTN_H) / 2;
        cancelButton =
                new FlatButton(
                        x + width - UiTheme.MODAL_PAD - 100,
                        buttonY,
                        48,
                        UiTheme.MODAL_BTN_H,
                        Component.translatable("preciseglower.edit.cancel"),
                        FlatButton.Style.NORMAL,
                        b -> close());
        saveButton =
                new FlatButton(
                        x + width - UiTheme.MODAL_PAD - 48,
                        buttonY,
                        48,
                        UiTheme.MODAL_BTN_H,
                        Component.translatable("preciseglower.edit.save"),
                        FlatButton.Style.PRIMARY,
                        b -> save());
        children.add(cancelButton);
        children.add(saveButton);

        validate();
    }

    private String idValue() {
        return switch (kind) {
            case BLOCK -> ((BlockRule) target).blockId();
            case ENTITY, HIDE_ENTITY -> ((EntityRule) target).type;
            case ITEM, HIDE_ITEM -> ((ItemRule) target).item;
        };
    }

    private FlatToggle addToggle(
            int left, int y, int width, String key, boolean value, Consumer<Boolean> onChange) {
        FlatToggle toggle =
                new FlatToggle(left, y, width, Component.translatable(key), value, onChange);
        children.add(toggle);
        return toggle;
    }

    private void setChannel(int channel, int value) {
        int r = channel == 0 ? value : Colors.red(target.color);
        int g = channel == 1 ? value : Colors.green(target.color);
        int b = channel == 2 ? value : Colors.blue(target.color);
        target.color = Colors.argb(Colors.alpha(target.color), r, g, b);
        syncColorWidgets();
    }

    private void syncColorSliders() {
        if (redSlider == null) return;
        redSlider.setValue(Colors.red(target.color));
        greenSlider.setValue(Colors.green(target.color));
        blueSlider.setValue(Colors.blue(target.color));
        alphaSlider.setValue(Colors.alpha(target.color));
    }

    private void syncColorWidgets() {
        if (colorPicker == null) return;
        colorPicker.setColor(target.color);
        syncColorSliders();
    }

    private List<Suggestion> suggestId(String text, int cursor) {
        String prefix = text.substring(0, Math.min(cursor, text.length()));
        List<Candidate> candidates =
                switch (kind) {
                    case BLOCK -> IdSuggester.blocks(prefix);
                    case ENTITY, HIDE_ENTITY -> IdSuggester.entities(prefix);
                    case ITEM, HIDE_ITEM -> IdSuggester.items(prefix);
                };
        List<Suggestion> out = new ArrayList<>();
        for (Candidate candidate : candidates) {
            out.add(new Suggestion(candidate.id(), 0, text.length(), candidate.label()));
        }
        return out;
    }

    private List<Suggestion> suggestFilter(String text, int cursor) {
        if (kind == Kind.BLOCK) {
            String id = idBox == null ? "" : idBox.getValue().trim();
            if (id.isEmpty()) id = "*";
            if ("*".equals(id)) return suggestWildcardState(text, cursor);
            String full = id + text;
            int offset = id.length();
            List<Suggestion> out = new ArrayList<>();
            for (Suggestion suggestion : BrigadierSuggestions.blockState(full, offset + cursor)) {
                int from = Math.max(0, suggestion.replaceFrom() - offset);
                int to = Math.max(from, suggestion.replaceTo() - offset);
                out.add(new Suggestion(suggestion.text(), from, to, suggestion.hint()));
            }
            return out;
        }
        List<CompoundTag> samples =
                kind == Kind.ENTITY || kind == Kind.HIDE_ENTITY
                        ? NbtSamples.forEntity(entityTypeInEdit())
                        : NbtSamples.forItem(itemInEdit());
        return NbtSuggester.suggest(text, cursor, samples);
    }

    private @Nullable EntityType<?> entityTypeInEdit() {
        EntityType<?> parsed = IdSuggester.entity(idText());
        return parsed != null ? parsed : ((EntityRule) target).entityType();
    }

    private @Nullable Item itemInEdit() {
        Item parsed = IdSuggester.item(idText());
        return parsed != null ? parsed : ((ItemRule) target).itemType();
    }

    private Component rangeLabel() {
        GlowerConfig config = PreciseGlowerClient.config();
        String base = Component.translatable("preciseglower.edit.range").getString();
        int cap =
                config == null
                        ? Rule.MAX_RANGE_CHUNKS
                        : kind == Kind.BLOCK
                                ? config.blockRangeCapChunks()
                                : config.entityRangeCapChunks();
        return cap < Rule.MAX_RANGE_CHUNKS
                ? Component.literal(base + " ≤" + cap)
                : Component.literal(base);
    }

    private String idText() {
        String id = idBox == null ? "" : idBox.getValue().trim();
        return "*".equals(id) ? "" : id;
    }

    private List<Suggestion> suggestWildcardState(String text, int cursor) {
        int end = Math.min(cursor, text.length());
        String prefix = text.substring(0, end);
        int open = prefix.lastIndexOf('[');
        if (open < 0) {
            if (prefix.trim().isEmpty())
                return List.of(new Suggestion("[", 0, prefix.length(), null));
            return List.of();
        }
        String after = prefix.substring(open + 1);
        int eq = after.lastIndexOf('=');
        int comma = after.lastIndexOf(',');
        if (eq > comma) {
            String name = after.substring(comma + 1, eq).trim();
            String valuePrefix = after.substring(eq + 1);
            int from = open + 1 + eq + 1;
            List<Suggestion> out = new ArrayList<>();
            for (String value : PropertyIndex.values(name)) {
                if (!value.toLowerCase().startsWith(valuePrefix.toLowerCase())) continue;
                out.add(new Suggestion(value, from, end, null));
                if (out.size() >= 24) break;
            }
            return out;
        }
        int from = open + 1 + comma + 1;
        String namePrefix = after.substring(Math.max(0, comma + 1));
        List<Suggestion> out = new ArrayList<>();
        for (String name : PropertyIndex.names()) {
            if (!name.toLowerCase().startsWith(namePrefix.toLowerCase())) continue;
            out.add(new Suggestion(name, from, end, null));
            if (out.size() >= 24) break;
        }
        return out;
    }

    private String filterValue() {
        String raw = filterBox == null ? "" : filterBox.getValue().trim();
        if (raw.isEmpty()) return "";
        if (kind == Kind.BLOCK && !raw.startsWith("[")) return "[" + raw + "]";
        return raw;
    }

    private String unknownKey() {
        return switch (kind) {
            case BLOCK -> "preciseglower.error.unknown_block";
            case ENTITY, HIDE_ENTITY -> "preciseglower.error.unknown_entity";
            case ITEM, HIDE_ITEM -> "preciseglower.error.unknown_item";
        };
    }

    private void validate() {
        String id = idBox == null ? "" : idBox.getValue().trim();
        if (id.isEmpty()) {
            statusOk = false;
            statusText = Component.translatable("preciseglower.status.empty_id").getString();
            return;
        }
        try {
            if (kind == Kind.BLOCK) {
                String blockId = id.contains("[") ? id.substring(0, id.indexOf('[')).trim() : id;
                if (!"*".equals(blockId)
                        && !blockId.startsWith("#")
                        && IdSuggester.block(blockId) == null) {
                    statusOk = false;
                    statusText = Component.translatable(unknownKey(), blockId).getString();
                    return;
                }
                if (blockId.startsWith("#")) {
                    if (!BlockStateFilter.tagsAvailable()) {
                        statusOk = false;
                        statusText =
                                Component.translatable(
                                                "preciseglower.error.tag_needs_world", blockId)
                                        .getString();
                        return;
                    }
                    if (!IdSuggester.blockTagExists(blockId)) {
                        statusOk = false;
                        statusText =
                                Component.translatable("preciseglower.error.unknown_tag", blockId)
                                        .getString();
                        return;
                    }
                }
                BlockStateFilter.parse(id + filterValue());
                if (filterValue().isEmpty()) {
                    statusOk = true;
                    statusText = "";
                    return;
                }
            } else {
                if (!"*".equals(id)) {
                    boolean known =
                            kind == Kind.ENTITY || kind == Kind.HIDE_ENTITY
                                    ? IdSuggester.entity(id.trim()) != null
                                    : IdSuggester.item(id.trim()) != null;
                    if (!known) {
                        statusOk = false;
                        statusText = Component.translatable(unknownKey(), id.trim()).getString();
                        return;
                    }
                }
                if (isCountBox()) {
                    String raw = filterBox == null ? "" : filterBox.getValue().trim();
                    if (parseCount(raw).isEmpty()) {
                        statusOk = false;
                        statusText =
                                Component.translatable("preciseglower.error.count").getString();
                        return;
                    }
                } else {
                    String nbt = filterBox == null ? "" : filterBox.getValue().trim();
                    if (!nbt.isEmpty()) NbtMatcher.parse(nbt);
                }
                if (filterBox == null || filterBox.getValue().trim().isEmpty()) {
                    statusOk = true;
                    statusText = "";
                    return;
                }
            }
            statusOk = true;
            statusText = Component.translatable("preciseglower.status.ok").getString();
        } catch (Exception e) {
            statusOk = false;
            statusText = Component.translatable("preciseglower.error.parse").getString();
        }
    }

    private void save() {
        if (colorPicker != null) colorPicker.syncFromHexBox();
        validate();
        if (!statusOk) return;
        String id = idBox == null ? "" : idBox.getValue().trim();
        String filter = filterBox == null ? "" : filterBox.getValue().trim();
        switch (kind) {
            case BLOCK -> ((BlockRule) target).id = id + filterValue();
            case ENTITY, HIDE_ENTITY -> {
                ((EntityRule) target).type = id;
                ((EntityRule) target).nbt = filter;
            }
            case ITEM -> {
                ((ItemRule) target).item = id;
                ((ItemRule) target).minCount = parseCount(filter).orElse(1);
            }
            case HIDE_ITEM -> {
                ((ItemRule) target).item = id;
                ((ItemRule) target).nbt = filter;
            }
        }
        if (onClose != null) onClose.accept(true);
    }

    private void close() {
        if (onClose != null) onClose.accept(false);
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = host.font();
        graphics.fill(x, y, x + width, y + height, UiTheme.MASK);
        graphics.fill(x, y, x + width, y + UiTheme.MODAL_HEAD_H, UiTheme.MASK);
        graphics.fill(
                x, y + UiTheme.MODAL_HEAD_H, x + width, y + UiTheme.MODAL_HEAD_H + 1, UiTheme.LINE);
        graphics.outline(x, y, width, height, UiTheme.LINE);
        String title =
                Component.translatable("preciseglower.edit.title").getString()
                        + " · "
                        + target.displayName();
        graphics.text(
                font,
                font.plainSubstrByWidth(title, width - CLOSE_W - 10),
                x + 6,
                y + 5,
                UiTheme.WHITE,
                false);

        boolean closeHover = isOverClose(mouseX, mouseY);
        graphics.outline(
                x + width - CLOSE_W,
                y,
                CLOSE_W,
                UiTheme.MODAL_HEAD_H,
                closeHover ? UiTheme.DANGER_SOLID : UiTheme.LINE);
        Graphics.drawX(
                graphics,
                x + width - 12,
                y + 4,
                8,
                closeHover ? UiTheme.DANGER_SOLID : UiTheme.WHITE);

        if (!statusText.isEmpty()) {
            graphics.text(
                    font,
                    statusText,
                    x + UiTheme.MODAL_PAD,
                    statusY,
                    statusOk ? UiTheme.WHITE : UiTheme.WARN,
                    false);
        }

        for (AbstractWidget child : children) {
            child.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        for (EditBox box : editBoxes) {
            if (box instanceof SuggestionEditBox suggestionBox && suggestionBox.isPopupOpen()) {
                graphics.nextStratum();
                suggestionBox.renderPopup(graphics, font, mouseX, mouseY);
            }
        }
    }

    private boolean isOverClose(double mouseX, double mouseY) {
        return mouseX >= x + width - CLOSE_W
                && mouseX <= x + width
                && mouseY >= y
                && mouseY <= y + UiTheme.MODAL_HEAD_H;
    }

    public void tick() {}

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (isOverClose(mx, my)) {
            close();
            return true;
        }
        if (mx < x || mx >= x + width || my < y || my >= y + height) return true;

        for (EditBox box : editBoxes) {
            if (box instanceof SuggestionEditBox suggestionBox
                    && suggestionBox.isPopupOpen()
                    && suggestionBox.popupMouseClicked(mx, my)) {
                return true;
            }
        }

        if (colorPicker != null && colorPicker.isMouseOver(mx, my)) {
            colorPicker.onClick(event, doubleClick);
            if (colorPicker.hexBox().isFocused()) setFocus(colorPicker.hexBox());
            return true;
        }

        for (AbstractWidget child : children) {
            if (child == colorPicker || !child.isMouseOver(mx, my)) continue;
            if (child instanceof EditBox box) {
                setFocus(box);
                box.onClick(event, doubleClick);
                if (child instanceof SuggestionEditBox suggestionBox)
                    suggestionBox.refreshSuggestions();
                return true;
            }
            draggingChild = child;
            child.mouseClicked(event, doubleClick);
            return true;
        }
        setFocus(null);
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingChild != null) {
            draggingChild.mouseReleased(event);
            draggingChild = null;
            return true;
        }
        for (AbstractWidget child : children) {
            if (child.isMouseOver(event.x(), event.y())) {
                child.mouseReleased(event);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingChild != null) return draggingChild.mouseDragged(event, dx, dy);
        for (EditBox box : editBoxes) {
            if (box instanceof SuggestionEditBox suggestionBox
                    && suggestionBox.isDraggingPopupBar()) {
                return suggestionBox.mouseDragged(event, dx, dy);
            }
        }
        for (AbstractWidget child : children) {
            if (child.isMouseOver(event.x(), event.y()) && child.mouseDragged(event, dx, dy))
                return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        for (EditBox box : editBoxes) {
            if (box instanceof SuggestionEditBox suggestionBox
                    && suggestionBox.popupMouseScrolled(mx, my, scrollY)) return true;
        }
        for (AbstractWidget child : children) {
            if (child.isMouseOver(mx, my) && child.mouseScrolled(mx, my, scrollX, scrollY))
                return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        for (EditBox box : editBoxes) {
            if (box.isFocused()) {
                boolean handled =
                        box instanceof SuggestionEditBox suggestionBox
                                ? suggestionBox.keyPressed(event)
                                : box.keyPressed(event);
                if (handled) return true;
            }
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN) {
            save();
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        for (EditBox box : editBoxes) {
            if (box.isFocused()) {
                boolean handled = box.charTyped(event);
                if (colorPicker != null && box == colorPicker.hexBox())
                    colorPicker.syncFromHexBox();
                return handled;
            }
        }
        return false;
    }

    private void setFocus(EditBox widget) {
        for (EditBox box : editBoxes) box.setFocused(box == widget);
        if (host != null) host.requestFocus(widget);
    }

    public void init(Font font) {
        setFocus(idBox);
    }
}
