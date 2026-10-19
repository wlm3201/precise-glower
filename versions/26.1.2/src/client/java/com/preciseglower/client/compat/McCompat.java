package com.preciseglower.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 26.1.2 平台适配：
 * <ul>
 *     <li>当前界面是 {@code Minecraft#screen} 字段（26.2 起移到 {@code Minecraft#gui}）</li>
 *     <li>游戏状态用 {@code GameRenderer#getGameRenderState()}（26.2 起是 {@code gameRenderState()}）</li>
 * </ul>
 */
public final class McCompat {
	private McCompat() {
	}

	public static Screen screen(Minecraft mc) {
		return mc.screen;
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.setScreen(screen);
	}

	/**
	 * 关卡渲染用的相机位置（已按 partialTick 插值）。
	 *
	 * <p>不能用 {@code getCameraEntity().position()}：那是 tick 坐标、没有插值，
	 * 描边会相对场景抖动。这里和 {@link #viewRotation} 同源，取自
	 * {@code CameraRenderState}。</p>
	 */
	public static Vec3 cameraPos(Minecraft mc) {
		if (mc.gameRenderer == null) return Vec3.ZERO;
		return mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.pos;
	}

	/**
	 * 关卡渲染的视图旋转矩阵。
	 *
	 * <p>不能拿 {@code RenderSystem.getModelViewMatrix()} 顶替：主关卡渲染期间它不含
	 * 关卡视图变换（26.1.2 的 LevelRenderer 压根不碰 modelView 栈），用它会让画面
	 * 整体偏移一个相机位置。</p>
	 */
	public static Matrix4f viewRotation(Minecraft mc) {
		if (mc.gameRenderer == null) return new Matrix4f();
		return new Matrix4f(mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix);
	}

	/** 26.1.2 / 26.2 使用 {@code Type.KEYSYM}，26.3 起改名为 {@code Type.KEYBOARD} */
	public static KeyMapping createKeyMapping(String name, int key, KeyMapping.Category category) {
		return new KeyMapping(name, InputConstants.Type.KEYSYM, key, category);
	}

	/** 26.1.2 同样有 {@code entityOutlineTarget} 与 {@code entity_outline} 后处理链，可以复用 */
	public static boolean supportsBlockOutline() {
		return true;
	}
}
