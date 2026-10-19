package com.preciseglower.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.search.BlockTracker;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 方块描边（26.1.2）：把高亮方块的表面写成一张彩色遮罩，追加到原版实体描边缓冲
 * {@code LevelRenderer#entityOutlineTarget} 里，交给 {@code entity_outline} 后处理链
 * 提取成和实体发光一致的辉光轮廓。
 *
 * <p>不清空原版缓冲（否则会擦掉实体发光），
 * 只追加自己的遮罩。</p>
 *
 * <p>26.1.2 用的是旧一版 blaze3d API：uniform 要用 {@code withUniform} 逐个声明
 * （没有 26.2+ 的 {@code BindGroupLayouts}），{@code drawIndexed} 参数顺序也不同。</p>
 */
public final class BlockOutlineRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("precise-glower");
	private static final String LABEL = "precise-glower block outline";
	/**
	 * 网格重建的最小间隔。约等于 1 tick，和线框（每帧实时遍历缓存）观感同步；
	 * 缓存没变化时根本不会触发重建，所以这里只是给「渐进扫描期间」兜底限流。
	 */
	private static final long REBUILD_INTERVAL_MS = 50L;
	/** 玩家离网格 origin 超过这个距离就立刻重建，避免大坐标下 float 顶点精度掉档 */
	private static final int ORIGIN_DRIFT = 32;

	private static final ByteBufferBuilder SCRATCH = new ByteBufferBuilder(1 << 20, 64L << 20);
	private static final BlockOutlineData DATA = new BlockOutlineData();
	private static final Vector4f WHITE = new Vector4f(1F, 1F, 1F, 1F);
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
	/** 默认按「原版有描边内容」处理：拿不到信号时宁可不清空，也不能擦掉实体发光 */
	private static boolean vanillaHasOutline = true;
	private static boolean broken;

	private BlockOutlineRenderer() {
	}

	/** 上一帧是否真的往描边缓冲里写了东西（用于放行原版的描边合成流程） */
	public static boolean hasMask() {
		return hasMask;
	}

	/** 由 mixin 回调：原版这一帧本身有没有实体描边内容 */
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

	/**
	 * 在原版描边阶段之后调用：把自己的方块遮罩追加进描边缓冲。
	 */
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

		// 玩家跑远了：顶点是相对 origin 的 float 坐标，不重建会掉精度
		BlockPos here = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
		boolean drifted = Math.abs(here.getX() - originX) > ORIGIN_DRIFT
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
			// 原版这一帧没有实体描边时，缓冲里可能还留着上一帧的遮罩，先清掉
			if (!vanillaHasOutline) clear(target);
			draw(target, mc);
			hasMask = true;
		} catch (Throwable t) {
			LOGGER.error("[precise-glower] 方块描边渲染失败，已关闭该效果", t);
			broken = true;
			reset();
		}
	}

	// ------------------------------------------------------------------ 网格

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

		BufferBuilder builder = new BufferBuilder(SCRATCH, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
			created = RenderSystem.getDevice()
				.createBuffer(() -> LABEL, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
		} finally {
			mesh.close();
			SCRATCH.clear();
		}
		// 旧缓冲可能还在 GPU 队列里，留到下一轮再释放
		if (staleBuffer != null) staleBuffer.close();
		staleBuffer = vertexBuffer;
		vertexBuffer = created;
		vertexCount = vertices;
		hasMask = true;
	}

	private static void clear(RenderTarget target) {
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
	}

	private static void draw(RenderTarget target, Minecraft mc) {
		// 视图矩阵只有旋转，平移要自己补：顶点是 (worldPos - origin)，
		// 所以需要 R * (worldPos - origin + origin - cam) == R * (worldPos - cam)
		Matrix4f modelView = McCompat.viewRotation(mc);
		Vec3 cam = McCompat.cameraPos(mc);
		modelView.translate(originX - (float) cam.x, originY - (float) cam.y, originZ - (float) cam.z);
		var dynamic = RenderSystem.getDynamicUniforms()
			.writeTransform(modelView, WHITE, NO_OFFSET, TEXTURE_MATRIX);
		int indexCount = vertexCount / 4 * 6;

		RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
			.createRenderPass(() -> LABEL, target.getColorTextureView(), OptionalInt.empty());
		try {
			pass.setPipeline(pipeline());
			var projection = RenderSystem.getProjectionMatrixBuffer();
			if (projection != null) pass.setUniform("Projection", projection);
			pass.setUniform("DynamicTransforms", dynamic);
			pass.setVertexBuffer(0, vertexBuffer);
			var sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			pass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type());
			// 26.1.2 的参数顺序是 (baseVertex, firstIndex, indexCount, instanceCount)
			pass.drawIndexed(0, 0, indexCount, 1);
		} finally {
			pass.close();
		}
	}

	private static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath("precise-glower", "pipeline/block_outline"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withVertexShader("core/position_color")
				.withFragmentShader("core/position_color")
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
				.withCull(false)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(Optional.empty())
				.build();
		}
		return pipeline;
	}

	private static void release() {
		// 延迟一拍释放：上一帧的顶点缓冲可能还挂在 GPU 命令队列里
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
