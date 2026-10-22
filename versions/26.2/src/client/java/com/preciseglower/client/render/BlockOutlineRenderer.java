package com.preciseglower.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.search.BlockTracker;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockOutlineRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("preciseglower");
    private static final String LABEL = "preciseglower block outline";

    private static final long REBUILD_INTERVAL_MS = 50L;

    private static final int ORIGIN_DRIFT = 32;

    private static final ByteBufferBuilder SCRATCH = new ByteBufferBuilder(1 << 20, 64L << 20);
    private static final BlockOutlineData DATA = new BlockOutlineData();
    private static final Vector4f WHITE = new Vector4f(1F, 1F, 1F, 1F);
    private static final Vector4f CLEAR = new Vector4f(0F, 0F, 0F, 0F);
    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer;
    private static GpuBuffer staleBuffer;
    private static int vertexCount;
    private static int originX;
    private static int originY;
    private static int originZ;
    private static boolean hasMask;
    private static boolean pending = true;
    private static long lastRebuild;

    private static boolean vanillaHasOutline = true;

    private static boolean broken;

    private BlockOutlineRenderer() {}

    public static boolean hasMask() {
        return hasMask;
    }

    public static void noteVanillaOutline(boolean value) {
        vanillaHasOutline = value;
    }

    public static void markDirty() {
        pending = true;
    }

    public static void reset() {
        release();
        hasMask = false;
        pending = true;
    }

    public static void renderOutline(RenderTarget target) {
        if (broken || target == null || !McCompat.supportsBlockOutline()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockTracker tracker = PreciseGlowerClient.blockTracker();
        if (mc == null || tracker == null || !tracker.outlineWanted()) {
            reset();
            return;
        }
        if (tracker.consumeOutlineDirty()) pending = true;

        BlockPos here = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        boolean drifted =
                Math.abs(here.getX() - originX) > ORIGIN_DRIFT
                        || Math.abs(here.getY() - originY) > ORIGIN_DRIFT
                        || Math.abs(here.getZ() - originZ) > ORIGIN_DRIFT;
        if (drifted) pending = true;

        long now = System.currentTimeMillis();
        if (pending && (drifted || now - lastRebuild >= REBUILD_INTERVAL_MS)) {
            lastRebuild = now;
            pending = false;
            rebuild(mc, mc.level, tracker);
        }
        if (vertexBuffer == null || vertexCount == 0) {
            hasMask = false;
            return;
        }
        try {
            if (!vanillaHasOutline) clear(target);
            draw(target, mc);
            hasMask = true;
        } catch (Throwable t) {
            LOGGER.error("[preciseglower] 方块描边渲染失败，已关闭该效果", t);
            broken = true;
            reset();
        }
    }

    private static void rebuild(Minecraft mc, ClientLevel level, BlockTracker tracker) {
        if (!tracker.collectOutline(level, DATA)) {
            release();
            vertexCount = 0;
            return;
        }
        BlockPos origin = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        originX = origin.getX();
        originY = origin.getY();
        originZ = origin.getZ();

        BufferBuilder builder =
                new BufferBuilder(
                        SCRATCH, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int vertices = DATA.emit(builder, originX, originY, originZ);
        MeshData mesh = builder.build();
        if (mesh == null || vertices == 0) {
            if (mesh != null) mesh.close();
            SCRATCH.clear();
            release();
            vertexCount = 0;
            return;
        }
        GpuBuffer created;
        try {
            created =
                    RenderSystem.getDevice()
                            .createBuffer(() -> LABEL, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
        } finally {
            mesh.close();
            SCRATCH.clear();
        }
        if (staleBuffer != null) staleBuffer.close();
        staleBuffer = vertexBuffer;
        vertexBuffer = created;
        vertexCount = vertices;
        hasMask = true;
    }

    private static void clear(RenderTarget target) {
        RenderSystem.getDevice()
                .createCommandEncoder()
                .clearColorTexture(target.getColorTexture(), CLEAR);
    }

    private static void draw(RenderTarget target, Minecraft mc) {
        Matrix4f modelView = McCompat.viewRotation(mc);
        Vec3 cam = McCompat.cameraPos(mc);
        modelView.translate(
                originX - (float) cam.x, originY - (float) cam.y, originZ - (float) cam.z);
        var dynamic =
                RenderSystem.getDynamicUniforms()
                        .writeTransform(modelView, WHITE, NO_OFFSET, TEXTURE_MATRIX);

        RenderPass pass =
                RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> LABEL, target.getColorTextureView(), Optional.empty());
        try {
            pass.setPipeline(pipeline());
            var projection = RenderSystem.getProjectionMatrixBuffer();
            if (projection != null) pass.setUniform("Projection", projection);
            pass.setUniform("DynamicTransforms", dynamic);
            pass.setVertexBuffer(0, vertexBuffer.slice());
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(sequential.getBuffer(vertexCount), sequential.type());
            pass.drawIndexed(vertexCount / 4 * 6, 1, 0, 0, 0);
        } finally {
            pass.close();
        }
    }

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline =
                    RenderPipeline.builder()
                            .withLocation(
                                    Identifier.fromNamespaceAndPath(
                                            "preciseglower", "pipeline/block_outline"))
                            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                            .withVertexShader("core/position_color")
                            .withFragmentShader("core/position_color")
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withCull(false)
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withDepthStencilState(Optional.empty())
                            .build();
        }
        return pipeline;
    }

    private static void release() {
        if (staleBuffer != null) {
            staleBuffer.close();
            staleBuffer = null;
        }
        if (vertexBuffer != null) {
            staleBuffer = vertexBuffer;
            vertexBuffer = null;
        }
        vertexCount = 0;
    }
}
