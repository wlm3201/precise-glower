package com.preciseglower.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 26.3 平台适配：与 26.2 相同，界面与摄像机都通过 {@code Minecraft#gui} / {@code GameRenderer#mainCamera()} 获取。 */
public final class McCompat {
	private McCompat() {
	}

	public static Screen screen(Minecraft mc) {
		return mc.gui.screen();
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.gui.setScreen(screen);
	}

	/**
	 * 关卡渲染用的相机位置。必须和 {@link #viewRotation} 同源（都取自
	 * {@code CameraRenderState}），否则描边会跟场景错开。
	 */
	public static Vec3 cameraPos(Minecraft mc) {
		if (mc.gameRenderer == null) return Vec3.ZERO;
		return mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos;
	}

	/**
	 * 关卡渲染的视图旋转矩阵。
	 *
	 * <p>不能拿 {@code RenderSystem.getModelViewMatrix()} 顶替：主关卡渲染期间它不含
	 * 关卡视图变换，用它会让画面整体偏移一个相机位置。</p>
	 */
	public static Matrix4f viewRotation(Minecraft mc) {
		if (mc.gameRenderer == null) return new Matrix4f();
		return new Matrix4f(mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix);
	}

	/** 26.3 起 {@code Type.KEYSYM} 改名为 {@code Type.KEYBOARD}（SDL 化） */
	public static KeyMapping createKeyMapping(String name, int key, KeyMapping.Category category) {
		return new KeyMapping(name, InputConstants.Type.KEYBOARD, key, category);
	}

	/** 26.2 起可以把方块提交进原版 outline 后处理，得到和实体发光一样的效果 */
	public static boolean supportsBlockOutline() {
		return true;
	}
}
