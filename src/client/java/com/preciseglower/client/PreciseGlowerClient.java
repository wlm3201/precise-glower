package com.preciseglower.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.config.ConfigStore;
import com.preciseglower.client.config.GlowerConfig;
import com.preciseglower.client.gui.GlowerScreen;
import com.preciseglower.client.render.BlockOutlineRenderer;
import com.preciseglower.client.search.BlockTracker;
import com.preciseglower.client.search.EntityTracker;
import com.preciseglower.client.search.HideTracker;
import com.preciseglower.client.util.AutoColor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreciseGlowerClient implements ClientModInitializer {
    public static final String MOD_ID = "preciseglower";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final BlockTracker BLOCK_TRACKER = new BlockTracker();
    private static final EntityTracker ENTITY_TRACKER = new EntityTracker();
    private static final HideTracker HIDE_TRACKER = new HideTracker();

    private static GlowerConfig config;
    private static KeyMapping openGuiKey;

    private static int autoGuiCountdown = -1;

    private static Object lastLevel;

    @Override
    public void onInitializeClient() {
        config = ConfigStore.load();

        openGuiKey =
                KeyMappingHelper.registerKeyMapping(
                        McCompat.createKeyMapping(
                                "key.preciseglower.open",
                                InputConstants.KEY_G,
                                KeyMapping.Category.register(
                                        Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(PreciseGlowerClient::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        ClientChunkEvents.CHUNK_LOAD.register(
                (level, chunk) ->
                        BLOCK_TRACKER.onChunkLoaded(level, chunk.getPos().x(), chunk.getPos().z()));
        ClientChunkEvents.CHUNK_UNLOAD.register(
                (level, chunk) ->
                        BLOCK_TRACKER.onChunkUnloaded(
                                level, chunk.getPos().x(), chunk.getPos().z()));

        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && System.getProperty("preciseglower.autogui") != null) {
            autoGuiCountdown = 80;
        }

        LOGGER.info("Precise Glower 已加载");
    }

    private static void onClientTick(Minecraft client) {
        if (client.level != null) {
            if (client.level != lastLevel) {
                lastLevel = client.level;
                AutoColor.invalidate();
                BlockOutlineRenderer.reset();
            }
            if (config.dirty) refreshRules();
            HIDE_TRACKER.onTick(client.level);
            BLOCK_TRACKER.onTick(client.level);
            ENTITY_TRACKER.onTick(client.level);
        }

        if (openGuiKey.consumeClick() && !(McCompat.screen(client) instanceof GlowerScreen)) {
            McCompat.setScreen(client, new GlowerScreen(null));
        }
        if (autoGuiCountdown > 0 && --autoGuiCountdown == 0) {
            McCompat.setScreen(client, new GlowerScreen(null));
        }
    }

    public static void refreshRules() {
        config.compileAll();
        BLOCK_TRACKER.setRules(config.blocks);
        BLOCK_TRACKER.setSettings(
                config.scanBudgetPerTick, config.blockMaxDistance, config.maxRenderedBlocks);
        ENTITY_TRACKER.setRules(config.entities, config.items);
        ENTITY_TRACKER.setMaxDistance(config.entityMaxDistance);
        HIDE_TRACKER.setRules(config.hiddenEntities, config.hiddenItems);
    }

    public static void saveConfig() {
        if (config != null) ConfigStore.save(config);
    }

    public static GlowerConfig config() {
        return config;
    }

    public static BlockTracker blockTracker() {
        return BLOCK_TRACKER;
    }

    public static EntityTracker entityTracker() {
        return ENTITY_TRACKER;
    }

    public static HideTracker hideTracker() {
        return HIDE_TRACKER;
    }

    public static KeyMapping openGuiKey() {
        return openGuiKey;
    }

    public static void renderGizmos() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || config == null) return;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        BLOCK_TRACKER.render(level);
        ENTITY_TRACKER.renderBoxes(partialTick);
    }
}
