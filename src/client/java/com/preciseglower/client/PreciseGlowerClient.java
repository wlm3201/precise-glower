package com.preciseglower.client;

import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.config.ConfigStore;
import com.preciseglower.client.config.GlowerConfig;
import com.preciseglower.client.gui.GlowerScreen;
import com.preciseglower.client.render.BlockOutlineRenderer;
import com.preciseglower.client.search.BlockTracker;
import com.preciseglower.client.search.EntityTracker;
import com.preciseglower.client.search.HideTracker;
import com.preciseglower.client.util.AutoColor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 客户端入口：加载配置、注册按键、驱动追踪器。 */
public class PreciseGlowerClient implements ClientModInitializer {
	public static final String MOD_ID = "precise-glower";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final BlockTracker BLOCK_TRACKER = new BlockTracker();
	private static final EntityTracker ENTITY_TRACKER = new EntityTracker();
	private static final HideTracker HIDE_TRACKER = new HideTracker();

	private static GlowerConfig config;
	private static KeyMapping openGuiKey;
	/** 仅开发环境：-Dpreciseglower.autogui=1 时启动后自动打开界面，便于截图比对 */
	private static int autoGuiCountdown = -1;
	private static Object lastLevel;

	@Override
	public void onInitializeClient() {
		config = ConfigStore.load();

		openGuiKey = KeyMappingHelper.registerKeyMapping(McCompat.createKeyMapping(
			"key.precise-glower.open",
			InputConstants.KEY_G,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(PreciseGlowerClient::onClientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

		// 区块加载 / 卸载：新区块一进来就扫描，卸载立即回收
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
			BLOCK_TRACKER.onChunkLoaded(level, chunk.getPos().x(), chunk.getPos().z()));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
			BLOCK_TRACKER.onChunkUnloaded(level, chunk.getPos().x(), chunk.getPos().z()));

		if (FabricLoader.getInstance().isDevelopmentEnvironment() && System.getProperty("preciseglower.autogui") != null) {
			autoGuiCountdown = 80;
		}

		LOGGER.info("Precise Glower 已加载");
	}

	private static void onClientTick(Minecraft client) {
		if (client.level != null) {
			if (client.level != lastLevel) {
				// 换了世界：注册表 / feature flags 都变了，创造物品栏索引要重建
				lastLevel = client.level;
				AutoColor.invalidate();
				// 描边网格是旧世界的，直接丢掉重建
				BlockOutlineRenderer.reset();
			}
			if (config.dirty) refreshRules();
			// 先更新隐藏快照，高亮追踪才能跳过被隐藏的实体
			HIDE_TRACKER.onTick(client.level);
			BLOCK_TRACKER.onTick(client.level);
			ENTITY_TRACKER.onTick(client.level);
		}

		// 主菜单也能打开界面（离线预览/编辑规则），只是命中数为 0
		if (openGuiKey.consumeClick() && !(McCompat.screen(client) instanceof GlowerScreen)) {
			McCompat.setScreen(client, new GlowerScreen(null));
		}
		if (autoGuiCountdown > 0 && --autoGuiCountdown == 0) {
			McCompat.setScreen(client, new GlowerScreen(null));
		}
	}

	/** 规则变化后调用：重新解析表达式并重建追踪 */
	public static void refreshRules() {
		// 注意不要再对列表排序：rules() 那只 List 的下标就是生效优先级，顺序由界面上的上下箭头决定，
		// 这里一旦重排就把用户手动调的顺序抹了（顺带连 implement 到追踪器里的下标也跟着错位）
		config.compileAll();
		BLOCK_TRACKER.setRules(config.blocks);
		BLOCK_TRACKER.setSettings(config.scanBudgetPerTick, config.blockMaxDistance, config.maxRenderedBlocks);
		ENTITY_TRACKER.setRules(config.entities, config.items);
		ENTITY_TRACKER.setMaxDistance(config.entityMaxDistance);
		HIDE_TRACKER.setRules(config.hiddenEntities, config.hiddenItems);
	}

	public static void saveConfig() {
		if (config != null) ConfigStore.save(config);
	}

	// ---------------------------------------------------------------- accessors

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

	/** 由版本特定的 mixin 在 gizmo 收集窗口内调用 */
	public static void renderGizmos() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || config == null) return;
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		BLOCK_TRACKER.render(level);
		ENTITY_TRACKER.renderBoxes(partialTick);
	}
}
