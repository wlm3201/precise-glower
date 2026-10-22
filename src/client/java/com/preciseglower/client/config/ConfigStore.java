package com.preciseglower.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("preciseglower");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigStore() {}

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("preciseglower.json");
    }

    public static GlowerConfig load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                GlowerConfig config = GSON.fromJson(json, GlowerConfig.class);
                if (config != null) {
                    if (config.blocks == null) config.blocks = new ArrayList<>();
                    if (config.entities == null) config.entities = new ArrayList<>();
                    if (config.items == null) config.items = new ArrayList<>();
                    if (config.hiddenEntities == null) config.hiddenEntities = new ArrayList<>();
                    if (config.hiddenItems == null) config.hiddenItems = new ArrayList<>();
                    LOGGER.info(
                            "[preciseglower] 已加载配置：{} 方块 / {} 实体 / {} 掉落物 / 隐藏 {} 实体 / 隐藏 {} 物品",
                            config.blocks.size(),
                            config.entities.size(),
                            config.items.size(),
                            config.hiddenEntities.size(),
                            config.hiddenItems.size());
                    config.markDirty();
                    return config;
                }
            } catch (Exception e) {
                LOGGER.error("[preciseglower] 读取配置失败，将使用默认配置", e);
            }
        }
        return new GlowerConfig();
    }

    public static void save(GlowerConfig config) {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[preciseglower] 保存配置失败", e);
        }
    }
}
