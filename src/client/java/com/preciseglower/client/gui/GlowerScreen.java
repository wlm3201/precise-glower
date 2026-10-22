package com.preciseglower.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.config.BlockRule;
import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.GlowerConfig;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.gui.autocomplete.Candidate;
import com.preciseglower.client.gui.autocomplete.IdSuggester;
import com.preciseglower.client.gui.autocomplete.NameIndex;
import com.preciseglower.client.gui.widget.CandidateListWidget;
import com.preciseglower.client.gui.widget.FlatButton;
import com.preciseglower.client.gui.widget.RuleEditModal;
import com.preciseglower.client.gui.widget.RuleListWidget;
import com.preciseglower.client.match.BlockStateFilter;
import com.preciseglower.client.util.AutoColor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class GlowerScreen extends Screen implements RuleEditModal.Host {
    public enum Tab {
        BLOCK,
        ENTITY,
        ITEM,
        HIDE_ENTITY,
        HIDE_ITEM
    }

    private static final int OFFSCREEN = -1000;

    private final Screen parent;
    private Tab tab;

    private EditBox searchBox;
    private CandidateListWidget candidateList;
    private RuleListWidget ruleList;
    private final List<Candidate> candidates = new ArrayList<>();

    private boolean selectAllSearch;

    private int keepCandidateIndex;

    private int keepCandidateScroll;

    private int keepRuleScroll;

    private boolean focusSearchPending;

    private boolean builtOnce;

    private RuleEditModal modal;
    private String keepSearch = "";

    private @Nullable String notice;

    public GlowerScreen(Screen parent) {
        super(Component.translatable("preciseglower.title"));
        this.parent = parent;
        GlowerConfig config = PreciseGlowerClient.config();
        this.tab = parseTab(config == null ? null : config.lastTab);
    }

    private static Tab parseTab(String value) {
        if (value != null) {
            for (Tab t : Tab.values()) if (t.name().equals(value)) return t;
        }
        return Tab.BLOCK;
    }

    @Override
    protected void init() {
        NameIndex.rebuild();
        buildUi();
    }

    private void buildUi() {
        int sideX = 6;
        int candW = UiTheme.SIDE_W - 12;
        int searchY = UiTheme.TAB_H + UiTheme.LIST_HEADER_H + 6;
        int searchW = candW - 52;

        searchBox =
                new EditBox(
                        font,
                        sideX,
                        searchY,
                        searchW,
                        UiTheme.CONTROL_H,
                        Component.translatable(searchKey()));
        searchBox.setMaxLength(256);
        searchBox.setValue(keepSearch);
        searchBox.setHint(Component.translatable("preciseglower.search.hint"));
        searchBox.setResponder(
                s -> {
                    keepSearch = s;
                    notice = null;
                    refreshCandidates(0);
                });
        addRenderableWidget(searchBox);

        addRenderableWidget(
                new FlatButton(
                        sideX + searchW + 4,
                        searchY,
                        48,
                        UiTheme.CONTROL_H,
                        Component.translatable("preciseglower.add"),
                        FlatButton.Style.PRIMARY,
                        b -> addRule(null)));

        candidateList =
                new CandidateListWidget(
                        sideX,
                        searchY + UiTheme.CONTROL_H + 4,
                        candW,
                        height - (searchY + UiTheme.CONTROL_H + 4) - 6,
                        new CandidateListWidget.Callbacks() {
                            @Override
                            public void onPick(int index) {
                                if (index >= 0 && index < candidates.size())
                                    addRule(candidates.get(index).id());
                            }

                            @Override
                            public void onCommit(int index) {
                                if (index >= 0 && index < candidates.size())
                                    addRule(candidates.get(index).id());
                            }
                        });
        addRenderableWidget(candidateList);

        int mainX = UiTheme.SIDE_W + 4;
        int listW = width - mainX - 4;
        int listY = UiTheme.TAB_H + UiTheme.LIST_HEADER_H;
        int listH = height - listY - 6;

        ruleList =
                new RuleListWidget(
                        mainX,
                        listY,
                        listW,
                        listH,
                        rules(),
                        new RuleListWidget.Callbacks() {
                            @Override
                            public void onEdit(int index) {
                                openEditor(index);
                            }

                            @Override
                            public void onDelete(int index) {
                                rules().remove(index);
                                changed();
                            }

                            @Override
                            public void onToggle(int index) {
                                Rule rule = rules().get(index);
                                rule.enabled = !rule.enabled;
                                changed();
                            }

                            @Override
                            public void onMove(int index, int delta) {
                                moveRule(index, delta);
                            }
                        },
                        styledTab());
        addRenderableWidget(ruleList);

        int headerY = UiTheme.TAB_H + 2;
        int clearW = font.width(Component.translatable("preciseglower.clear").getString()) + 10;
        int disableW =
                font.width(Component.translatable("preciseglower.disable_all").getString()) + 10;
        int enableW =
                font.width(Component.translatable("preciseglower.enable_all").getString()) + 10;
        int btnRight = width - 4;
        addRenderableWidget(
                new FlatButton(
                                btnRight - clearW,
                                headerY,
                                clearW,
                                UiTheme.CONTROL_H,
                                Component.translatable("preciseglower.clear"),
                                FlatButton.Style.DANGER,
                                b -> clearRules())
                        .requireConfirm());
        btnRight -= clearW + 4;
        addRenderableWidget(
                new FlatButton(
                                btnRight - disableW,
                                headerY,
                                disableW,
                                UiTheme.CONTROL_H,
                                Component.translatable("preciseglower.disable_all"),
                                FlatButton.Style.NORMAL,
                                b -> setAllEnabled(false))
                        .hoverBorder(UiTheme.DANGER_SOLID)
                        .hoverText(UiTheme.DANGER_SOLID));
        btnRight -= disableW + 4;
        addRenderableWidget(
                new FlatButton(
                                btnRight - enableW,
                                headerY,
                                enableW,
                                UiTheme.CONTROL_H,
                                Component.translatable("preciseglower.enable_all"),
                                FlatButton.Style.NORMAL,
                                b -> setAllEnabled(true))
                        .hoverBorder(UiTheme.ACCENT)
                        .hoverText(UiTheme.ACCENT));

        refreshCandidates(keepCandidateIndex);
        if (candidateList != null) candidateList.restoreScroll(keepCandidateScroll);
        if (ruleList != null) ruleList.setScroll(keepRuleScroll);
        if (!builtOnce) {
            builtOnce = true;
            setInitialFocus(searchBox);
        }
        if (selectAllSearch && !keepSearch.isEmpty()) {
            searchBox.setHighlightPos(0);
            searchBox.moveCursorToEnd(true);
        } else {
            searchBox.moveCursorToEnd(false);
        }
        selectAllSearch = false;
        keepCandidateIndex = 0;
        keepCandidateScroll = 0;
        keepRuleScroll = 0;
    }

    private String searchKey() {
        return "preciseglower.search.title";
    }

    private boolean styledTab() {
        return tab == Tab.BLOCK || tab == Tab.ENTITY || tab == Tab.ITEM;
    }

    private String unknownKey() {
        return switch (tab) {
            case BLOCK -> "preciseglower.error.unknown_block";
            case ENTITY, HIDE_ENTITY -> "preciseglower.error.unknown_entity";
            case ITEM, HIDE_ITEM -> "preciseglower.error.unknown_item";
        };
    }

    private List<? extends Rule> rules() {
        GlowerConfig config = PreciseGlowerClient.config();
        return switch (tab) {
            case BLOCK -> config.blocks;
            case ENTITY -> config.entities;
            case ITEM -> config.items;
            case HIDE_ENTITY -> config.hiddenEntities;
            case HIDE_ITEM -> config.hiddenItems;
        };
    }

    private void refreshCandidates(int selectIndex) {
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        List<Candidate> result =
                switch (tab) {
                    case BLOCK -> IdSuggester.blocks(query);
                    case ENTITY, HIDE_ENTITY -> IdSuggester.entities(query);
                    case ITEM, HIDE_ITEM -> IdSuggester.items(query);
                };
        candidates.clear();
        candidates.addAll(result);
        if (candidateList != null) {
            candidateList.setEntries(candidates);
            int index =
                    candidates.isEmpty()
                            ? -1
                            : Math.max(0, Math.min(selectIndex, candidates.size() - 1));
            candidateList.setSelected(index);
        }
    }

    private void addRule(@Nullable String explicit) {
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        String text = explicit != null ? explicit : query;
        if (text.isEmpty()) {
            notice = Component.translatable("preciseglower.status.empty_id").getString();
            return;
        }
        GlowerConfig config = PreciseGlowerClient.config();
        notice = null;
        if (tab == Tab.BLOCK && text.startsWith("#") && !BlockStateFilter.tagsAvailable()) {
            notice =
                    Component.translatable("preciseglower.error.tag_needs_world", text).getString();
            return;
        }
        boolean known =
                switch (tab) {
                    case BLOCK -> IdSuggester.blockExists(text);
                    case ENTITY, HIDE_ENTITY -> IdSuggester.entityExists(text);
                    case ITEM, HIDE_ITEM -> IdSuggester.itemExists(text);
                };
        if (!known) {
            notice = Component.translatable(unknownKey(), text).getString();
            return;
        }
        switch (tab) {
            case BLOCK -> {
                BlockRule rule = new BlockRule();
                rule.id = text;
                rule.color = AutoColor.forBlock(text);
                insertByPriority(config.blocks, rule);
            }
            case ENTITY -> {
                EntityRule rule = new EntityRule();
                rule.type = text;
                rule.color = AutoColor.forEntity(text);
                insertByPriority(config.entities, rule);
            }
            case ITEM -> {
                ItemRule rule = new ItemRule();
                rule.item = text;
                rule.color = AutoColor.forItem(text);
                insertByPriority(config.items, rule);
            }
            case HIDE_ENTITY -> {
                EntityRule rule = new EntityRule();
                rule.type = text;
                config.hiddenEntities.add(0, rule);
            }
            case HIDE_ITEM -> {
                ItemRule rule = new ItemRule();
                rule.item = text;
                config.hiddenItems.add(0, rule);
            }
        }
        keepSearch = query;
        selectAllSearch = !query.isEmpty();
        focusSearchPending = !query.isEmpty();
        changed();
    }

    private static <T extends Rule> void insertByPriority(List<T> list, T rule) {
        int score = rule.specificity();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).specificity() <= score) {
                list.add(i, rule);
                return;
            }
        }
        list.add(rule);
    }

    private void moveRule(int index, int delta) {
        List<? extends Rule> list = rules();
        int to = index + delta;
        if (index < 0 || to < 0 || index >= list.size() || to >= list.size()) return;
        Collections.swap(list, index, to);
        changed();
    }

    private void openEditor(int index) {
        Rule source = rules().get(index);
        Rule copy = source.copy();
        RuleEditModal.Kind kind =
                switch (tab) {
                    case BLOCK -> RuleEditModal.Kind.BLOCK;
                    case ENTITY -> RuleEditModal.Kind.ENTITY;
                    case ITEM -> RuleEditModal.Kind.ITEM;
                    case HIDE_ENTITY -> RuleEditModal.Kind.HIDE_ENTITY;
                    case HIDE_ITEM -> RuleEditModal.Kind.HIDE_ITEM;
                };
        modal =
                new RuleEditModal(
                        this,
                        kind,
                        copy,
                        saved -> {
                            if (saved) {
                                replaceRule(index, copy);
                                changed();
                            }
                            modal = null;
                        });
        modal.init(font);
    }

    private void replaceRule(int index, Rule rule) {
        GlowerConfig config = PreciseGlowerClient.config();
        switch (tab) {
            case BLOCK -> config.blocks.set(index, (BlockRule) rule);
            case ENTITY -> config.entities.set(index, (EntityRule) rule);
            case ITEM -> config.items.set(index, (ItemRule) rule);
            case HIDE_ENTITY -> config.hiddenEntities.set(index, (EntityRule) rule);
            case HIDE_ITEM -> config.hiddenItems.set(index, (ItemRule) rule);
        }
    }

    private void changed() {
        PreciseGlowerClient.refreshRules();
        PreciseGlowerClient.saveConfig();
        keepCandidateIndex = candidateList == null ? 0 : Math.max(0, candidateList.selected());
        keepCandidateScroll = candidateList == null ? 0 : candidateList.scroll();
        keepRuleScroll = ruleList == null ? 0 : ruleList.scroll();
        clearWidgets();
        buildUi();
    }

    private void switchTab(Tab next) {
        if (next == tab) return;
        tab = next;
        GlowerConfig config = PreciseGlowerClient.config();
        if (config != null) config.lastTab = tab.name();
        modal = null;
        keepSearch = "";
        notice = null;
        clearWidgets();
        buildUi();
    }

    private void setAllEnabled(boolean enabled) {
        for (Rule rule : rules()) rule.enabled = enabled;
        changed();
    }

    private void clearRules() {
        rules().clear();
        changed();
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, UiTheme.MASK);
        graphics.fill(UiTheme.SIDE_W, UiTheme.TAB_H, UiTheme.SIDE_W + 1, height, UiTheme.LINE);

        Graphics.tabs(
                graphics,
                font,
                width,
                tab,
                modal == null ? mouseX : OFFSCREEN,
                modal == null ? mouseY : OFFSCREEN);
        if (notice == null)
            Graphics.sideHeader(graphics, font, Component.translatable(searchKey()).getString());
        else Graphics.sideHeader(graphics, font, notice, UiTheme.WARN);
        Graphics.mainHeader(
                graphics, font, width, Component.translatable("preciseglower.rules").getString());
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int behindX = modal == null ? mouseX : OFFSCREEN;
        int behindY = modal == null ? mouseY : OFFSCREEN;
        super.extractRenderState(graphics, behindX, behindY, partialTick);
        if (modal != null) {
            graphics.nextStratum();
            modal.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (modal != null) modal.tick();
        if (focusSearchPending) {
            focusSearchPending = false;
            if (modal == null && searchBox != null) {
                setFocused(searchBox);
                searchBox.setHighlightPos(0);
                searchBox.moveCursorToEnd(true);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (modal != null) return modal.mouseClicked(event, doubleClick);

        if (my >= 0 && my < UiTheme.TAB_H && mx >= width - UiTheme.TAB_H) {
            onClose();
            return true;
        }
        if (my >= 0 && my < UiTheme.TAB_H && mx >= 0 && mx < UiTheme.TAB_W * Tab.values().length) {
            switchTab(Tab.values()[(int) (mx / UiTheme.TAB_W)]);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (modal != null) return modal.mouseReleased(event);
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (modal != null) return modal.mouseDragged(event, deltaX, deltaY);
        if (candidateList != null && candidateList.mouseDragged(event, deltaX, deltaY)) return true;
        if (ruleList != null && ruleList.mouseDragged(event, deltaX, deltaY)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (modal != null) return modal.mouseScrolled(x, y, scrollX, scrollY);
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modal != null) return modal.keyPressed(event);
        if (event.key() == InputConstants.KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            int key = event.key();
            if (key == InputConstants.KEY_DOWN) {
                candidateList.moveSelection(1);
                return true;
            }
            if (key == InputConstants.KEY_UP) {
                candidateList.moveSelection(-1);
                return true;
            }
            if (key == InputConstants.KEY_TAB) {
                int index = candidateList.selected();
                if (index >= 0 && index < candidates.size()) {
                    searchBox.setValue(candidates.get(index).id());
                }
                return true;
            }
            if (key == InputConstants.KEY_RETURN) {
                int index = candidateList.selected();
                if (index >= 0 && index < candidates.size()) {
                    addRule(candidates.get(index).id());
                } else {
                    addRule(null);
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal != null) return modal.charTyped(event);
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        GlowerConfig config = PreciseGlowerClient.config();
        if (config != null) config.lastTab = tab.name();
        PreciseGlowerClient.saveConfig();
        if (minecraft != null) McCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void requestFocus(AbstractWidget widget) {
        setFocused(widget);
    }

    @Override
    public int screenWidth() {
        return width;
    }

    @Override
    public int screenHeight() {
        return height;
    }

    @Override
    public Font font() {
        return font;
    }
}
