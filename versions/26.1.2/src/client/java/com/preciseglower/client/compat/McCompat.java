package com.preciseglower.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class McCompat {
    private McCompat() {}

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    public static Vec3 cameraPos(Minecraft mc) {
        if (mc.gameRenderer == null) return Vec3.ZERO;
        return mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.pos;
    }

    public static Matrix4f viewRotation(Minecraft mc) {
        if (mc.gameRenderer == null) return new Matrix4f();
        return new Matrix4f(
                mc.gameRenderer.getGameRenderState()
                        .levelRenderState
                        .cameraRenderState
                        .viewRotationMatrix);
    }

    public static KeyMapping createKeyMapping(String name, int key, KeyMapping.Category category) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, key, category);
    }

    public static boolean supportsBlockOutline() {
        return true;
    }
}
