package com.preciseglower.client.search;

import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.config.BlockRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.render.BlockOutlineData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 方块实时追踪：
 * <ul>
 *     <li>区块加载事件驱动扫描（新区块一进来就会被扫，不需要重启规则）</li>
 *     <li>{@code ClientLevel#setBlock} 触发的增量更新</li>
 *     <li>区块卸载 / 超出半径自动回收</li>
 * </ul>
 * 渲染时做相邻面剔除（两个高亮方块之间的公共面不画）。
 */
public final class BlockTracker {
	private static final Direction[] FACES = Direction.values();
	/**
	 * 描边面数预算：每个方块按 96 个面算。
	 *
	 * <p>形状不只一个盒子的方块面数很可观 —— 实测（26.2 全表统计）：漏斗 13 盒 78 面、
	 * 炼药锅 15 盒 90 面、讲台 9 盒、铁砧 7 盒，共 148 种方块超过 8 面。
	 * 之前按 8 面/块给预算，等于只允许「一个盒子」的方块，碰到漏斗/炼药锅
	 * 这类就直接截断，表现为「石头没事、漏斗画不出来」。</p>
	 */
	private static final int FACES_PER_BLOCK = 96;

	/** 区块 -> (方块位置 -> 命中规则的位掩码)；掩码而不是单条规则，是为了让范围在渲染期再判 */
	private final Map<Long, Map<Long, Long>> chunks = new HashMap<>();
	private final Set<Long> scanned = new HashSet<>();
	private final Deque<Long> pending = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();

	/** 是否有规则使用描边模式（决定要不要往原版 outline 缓冲里写东西） */
	private volatile boolean outlineWanted;
	/** 描边用的方块清单需要重建 */
	private volatile boolean outlineDirty = true;

	private int[] hitCounts = new int[0];
	private int renderedBlocks;
	private boolean truncated;

	private List<BlockRule> rules = new ArrayList<>();
	/** 当前生效的过滤器（避免每次 section 预筛都重新构造 lambda） */
	private List<Predicate<BlockState>> activePredicates = List.of();
	/** 上一次 setRules 时的匹配条件快照 */
	private List<String> lastMatcherKeys = List.of();
	/** 规则刚变过：短时间用更大的预算把缓存补回来 */
	private boolean rescanBoost;
	private int scanBudget = 4;
	/** 全局上限（格），每规则的范围还要再跟它取 min */
	private int maxDistance = 96;
	private int maxRendered = 8000;
	/** 每条规则的高亮半径（区块，切比雪夫距离），下标与 rules 对齐 */
	private int[] ruleRadius = new int[0];
	/** 扫描半径（区块）取所有规则里最大的那个，决定要覆盖哪些区块 */
	private int scanRadius = 6;

	// render 期临时缓存（避免每帧重复分配）
	private long lastChunkKey = Long.MIN_VALUE;
	private Map<Long, Long> lastChunkMap;

	public void setRules(List<BlockRule> rules) {
		// 注意：外面传进来的是 config 里同一个 List 实例，不能拿 this.rules 跟它比，
		// 否则永远是「自己跟自己比」。这里保存上一次的匹配条件快照再比较。
		// 只有「匹配条件」变了才需要重扫：改颜色 / 线宽 / 透明度不该让画面熄灭
		boolean matchingChanged = !matcherKeys(rules).equals(lastMatcherKeys);
		lastMatcherKeys = matcherKeys(rules);
		this.rules = rules;
		this.hitCounts = new int[rules.size()];
		List<Predicate<BlockState>> predicates = new ArrayList<>();
		for (BlockRule rule : rules) {
			if (rule.enabled && rule.error() == null && rule.filter() != null) {
				predicates.add(rule.filter()::matches);
			}
		}
		this.activePredicates = predicates;
		this.outlineWanted = false;
		for (BlockRule rule : rules) {
			if (rule.enabled && rule.error() == null && rule.filter() != null && rule.blockMode().outline()) {
				outlineWanted = true;
				break;
			}
		}
		outlineDirty = true;
		recomputeRanges();
		if (matchingChanged) rescanAll();
	}

	/**
	 * 把「每规则范围」和「全局上限」合成实际生效的水平区块半径。
	 * 全局设置仍然是硬上限（保护性能），规则的滑动条只在它之内再收窄。
	 *
	 * <p>范围是水平方向的切比雪夫区块距离：1 = 玩家所在区块（1×1），
	 * 2 = 3×3 区块，N = (2N-1)×(2N-1) 区块；垂直方向不限制。</p>
	 */
	private void recomputeRanges() {
		int[] radius = new int[rules.size()];
		int maxChunks = 1;
		int globalChunks = globalRangeChunks();
		for (int i = 0; i < rules.size(); i++) {
			int chunks = Math.min(globalChunks, rules.get(i).rangeChunks());
			radius[i] = chunks - 1;
			if (chunks > maxChunks) maxChunks = chunks;
		}
		ruleRadius = radius;
		scanRadius = maxChunks - 1;
	}

	private int globalRangeChunks() {
		return Math.max(1, maxDistance >> 4);
	}

	/** 世界坐标 -> 区块坐标（负数方向要取 floor，不能直接整除） */
	private static int floorChunk(double value) {
		return (int) Math.floor(value) >> 4;
	}

	private static int floorChunk(int blockCoord) {
		return blockCoord >> 4;
	}

	/** 规则的「匹配条件」快照：启用状态 + 表达式 */
	private static List<String> matcherKeys(List<BlockRule> rules) {
		List<String> keys = new ArrayList<>(rules.size());
		for (BlockRule rule : rules) {
			keys.add((rule.enabled ? "1" : "0") + "|" + rule.id);
		}
		return keys;
	}

	public void setSettings(int scanBudget, int maxDistance, int maxRendered) {
		this.scanBudget = Math.max(1, scanBudget);
		this.maxDistance = Math.max(16, maxDistance);
		this.maxRendered = Math.max(100, maxRendered);
		// setRules / setSettings 谁后调用都行：两边都重算一次
		recomputeRanges();
		// 这几个值只影响渲染与调度，不需要重新扫描
	}

	/**
	 * 规则变了：把已经扫过的区块重新排队，但**保留现有结果**。
	 * 这样画面是「旧的慢慢被新的替换」而不是先整屏熄灭。
	 */
	private void rescanAll() {
		synchronized (chunks) {
			rescanBoost = true;
			for (Long key : scanned) {
				if (queued.add(key)) pending.add(key);
			}
		}
	}

	public void reset() {
		synchronized (chunks) {
			chunks.clear();
			scanned.clear();
			queued.clear();
			pending.clear();
		}
		outlineDirty = true;
	}

	/** 是否有规则走描边模式 */
	public boolean outlineWanted() {
		return outlineWanted;
	}

	/** 描边清单是否需要重建 */
	public boolean consumeOutlineDirty() {
		if (!outlineDirty) return false;
		outlineDirty = false;
		return true;
	}

	public int hitCount(int ruleIndex) {
		return ruleIndex >= 0 && ruleIndex < hitCounts.length ? hitCounts[ruleIndex] : 0;
	}

	public int renderedBlocks() {
		return renderedBlocks;
	}

	public boolean truncated() {
		return truncated;
	}

	// ------------------------------------------------------------ chunk events

	/** 区块加载：立刻（重新）排入扫描队列 —— 这是新区块能马上高亮的关键 */
	public void onChunkLoaded(ClientLevel level, int cx, int cz) {
		if (rules.isEmpty()) return;
		long key = ChunkPos.pack(cx, cz);
		synchronized (chunks) {
			scanned.remove(key);
			enqueue(key);
		}
	}

	public void onChunkUnloaded(ClientLevel level, int cx, int cz) {
		long key = ChunkPos.pack(cx, cz);
		synchronized (chunks) {
			chunks.remove(key);
			scanned.remove(key);
			queued.remove(key);
			pending.remove(key);
		}
		outlineDirty = true;
	}

	private void enqueue(long key) {
		if (queued.add(key)) pending.add(key);
	}

	// ------------------------------------------------------------------ tick

	public void onTick(ClientLevel level) {
		if (level == null) return;
		if (rules.isEmpty()) {
			if (!chunks.isEmpty()) reset();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		BlockPos origin = mc.player.blockPosition();
		int radius = scanRadius;
		int cx0 = SectionPos.posToSectionCoord(origin.getX());
		int cz0 = SectionPos.posToSectionCoord(origin.getZ());

		synchronized (chunks) {
			// 超出半径的缓存直接丢弃
			boolean[] dropped = { false };
			chunks.keySet().removeIf(key -> {
				int x = ChunkPos.getX(key);
				int z = ChunkPos.getZ(key);
				if (Math.abs(x - cx0) > radius || Math.abs(z - cz0) > radius) {
					scanned.remove(key);
					queued.remove(key);
					pending.remove(key);
					dropped[0] = true;
					return true;
				}
				return false;
			});
			if (dropped[0]) outlineDirty = true;
			scanned.removeIf(key -> {
				int x = ChunkPos.getX(key);
				int z = ChunkPos.getZ(key);
				if (Math.abs(x - cx0) > radius || Math.abs(z - cz0) > radius) {
					queued.remove(key);
					pending.remove(key);
					return true;
				}
				return false;
			});

			// 兜底：半径内已加载但还没扫过的区块（事件漏掉时也能补上）
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					long key = ChunkPos.pack(cx0 + dx, cz0 + dz);
					if (scanned.contains(key) || queued.contains(key)) continue;
					if (level.getChunk(cx0 + dx, cz0 + dz, ChunkStatus.FULL, false) == null) continue;
					enqueue(key);
				}
			}

			// 规则刚变过时提高预算，让缓存几 tick 内补齐（而不是按常规预算慢慢爬）
			int total = pending.size();
			int budget;
			if (rescanBoost) {
				if (total == 0) rescanBoost = false;
				budget = Math.max(scanBudget, (total + 5) / 6);
			} else {
				budget = scanBudget;
			}
			budget = Math.min(budget, total);
			for (int i = 0; i < budget; i++) {
				Long key = pending.poll();
				if (key == null) break;
				queued.remove(key);
				scanned.add(key);
				scanChunk(level, key);
			}
		}
	}

	private void scanChunk(ClientLevel level, long chunkKey) {
		int cx = ChunkPos.getX(chunkKey);
		int cz = ChunkPos.getZ(chunkKey);
		ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) return;

		Map<Long, Long> found = new HashMap<>();
		LevelChunkSection[] sections = chunk.getSections();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir()) continue;
			int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
			PalettedContainer<BlockState> states = section.getStates();
			// 先用 palette 判断这个 section 有没有可能命中，避免白遍历 4096 个位置
			if (!sectionMaybeMatches(states)) continue;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = states.get(x, y, z);
						if (state.isAir()) continue;
						long mask = matchMask(state);
						if (mask == 0L) continue;
						pos.set((cx << 4) + x, baseY + y, (cz << 4) + z);
						found.put(pos.asLong(), mask);
					}
				}
			}
		}

		synchronized (chunks) {
			if (found.isEmpty()) chunks.remove(chunkKey);
			else chunks.put(chunkKey, found);
		}
		outlineDirty = true;
	}

	private boolean sectionMaybeMatches(PalettedContainer<BlockState> states) {
		for (int i = 0; i < activePredicates.size(); i++) {
			if (states.maybeHas(activePredicates.get(i))) return true;
		}
		return false;
	}

	/**
	 * 命中的全部规则按 {@code rules} 下标（= 优先级顺序，下标越小越优先）压成位掩码。
	 *
	 * <p>扫描期还不知道这个方块相对玩家的位置，没法判范围，所以必须把所有候选都留下，
	 * 由 {@link #pick(int, int, int, int, long)} 在渲染期挑。规则数超过 64 条时只取前 64 条
	 * （排序后它们优先级最低，被截掉的基本只会是通配符规则）。</p>
	 */
	private long matchMask(BlockState state) {
		long mask = 0L;
		int limit = Math.min(rules.size(), Long.SIZE);
		for (int i = 0; i < limit; i++) {
			BlockRule rule = rules.get(i);
			if (!rule.enabled || rule.error() != null || rule.filter() == null) continue;
			if (rule.filter().matches(state)) mask |= 1L << i;
		}
		return mask;
	}

	/**
	 * 在候选里挑真正生效的那条：优先级最高、且自己的范围覆盖该方块。
	 * 返回 -1 表示没有任何候选的范围覆盖到这里。
	 */
	private int pick(int pcx, int pcz, int bx, int bz, long mask) {
		int limit = Math.min(rules.size(), Long.SIZE);
		for (int i = 0; i < limit; i++) {
			if ((mask & (1L << i)) == 0L) continue;
			// 水平区块切比雪夫距离，垂直不限
			if (Math.abs(floorChunk(bx) - pcx) > ruleRadius[i]) continue;
			if (Math.abs(floorChunk(bz) - pcz) > ruleRadius[i]) continue;
			return i;
		}
		return -1;
	}

	// ------------------------------------------------------------ block update

	/** 由 mixin 在 {@code Level#setBlock} 之后调用（客户端侧） */
	public void onBlockChanged(BlockPos pos, BlockState state) {
		if (rules.isEmpty()) return;
		long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		synchronized (chunks) {
			if (!scanned.contains(chunkKey)) return; // 该区块还没扫过，等区块扫描覆盖
			Map<Long, Long> map = chunks.get(chunkKey);
			long mask = state.isAir() ? 0L : matchMask(state);
			if (mask == 0L) {
				if (map != null && map.remove(pos.asLong()) != null) outlineDirty = true;
			} else {
				if (map == null) chunks.put(chunkKey, map = new HashMap<>());
				Long previous = map.put(pos.asLong(), mask);
				if (previous == null || previous != mask) outlineDirty = true;
			}
		}
	}

	// ------------------------------------------------------- 描边模式数据

	/**
	 * 收集描边模式需要的方块面清单。
	 * 只在缓存变化时调用，结果会被上传成静态网格反复使用。
	 *
	 * <p>形状取 {@code BlockState#getShape}（和原版方块选择框同源），所以漏斗、台阶
	 * 这类不完整方块的描边是贴合真实轮廓的。</p>
	 */
	public boolean collectOutline(ClientLevel level, BlockOutlineData out) {
		out.reset();
		if (rules.isEmpty() || !outlineWanted || level == null) return false;

		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = McCompat.cameraPos(mc);
		int pcx = floorChunk(cam.x());
		int pcz = floorChunk(cam.z());
		// 面数上限：按「每块最多 96 个面」给预算（见 FACES_PER_BLOCK 的说明）
		out.limit = Math.min(maxRendered * FACES_PER_BLOCK, BlockOutlineData.MAX_FACES);
		lastChunkKey = Long.MIN_VALUE;
		lastChunkMap = null;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		synchronized (chunks) {
			for (Map<Long, Long> map : chunks.values()) {
				for (Map.Entry<Long, Long> entry : map.entrySet()) {
					if (out.count >= out.limit) break;
					long packed = entry.getKey();
					int bx = BlockPos.getX(packed);
					int by = BlockPos.getY(packed);
					int bz = BlockPos.getZ(packed);
					int index = pick(pcx, pcz, bx, bz, entry.getValue());
					if (index < 0 || index >= rules.size()) continue;

					BlockRule rule = rules.get(index);
					if (!rule.enabled || !rule.blockMode().outline()) continue;

					BlockState state = level.getBlockState(pos.set(bx, by, bz));
					VoxelShape shape = state.getShape(level, pos);
					if (shape.isEmpty()) continue;
					int argb = rule.opaqueColor();
					// shape 的每个组成盒子的全部面都写进去：遮罩是填充的，
					// 内部面多画不影响轮廓，还能省掉整套共面合并逻辑
					shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
						for (int d = 0; d < FACES.length; d++) {
							out.addFace(bx, by, bz, x0, y0, z0, x1, y1, z1, d, argb);
						}
					});
				}
				if (out.count >= out.limit) break;
			}
		}
		return out.count > 0;
	}

	// ---------------------------------------------------------------- render

	/** 在 gizmo 收集窗口内调用，提交方块线框；返回本次渲染数量 */
	public int render(ClientLevel level) {
		renderedBlocks = 0;
		truncated = false;
		if (rules.isEmpty() || level == null) return 0;
		hitCounts = new int[rules.size()];

		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = McCompat.cameraPos(mc);
		int pcx = floorChunk(cam.x());
		int pcz = floorChunk(cam.z());
		lastChunkKey = Long.MIN_VALUE;
		lastChunkMap = null;

		synchronized (chunks) {
			for (Map<Long, Long> map : chunks.values()) {
				for (Map.Entry<Long, Long> entry : map.entrySet()) {
					if (renderedBlocks >= maxRendered) {
						truncated = true;
						return renderedBlocks;
					}
					long packed = entry.getKey();
					int bx = BlockPos.getX(packed);
					int by = BlockPos.getY(packed);
					int bz = BlockPos.getZ(packed);
					int index = pick(pcx, pcz, bx, bz, entry.getValue());
					if (index < 0 || index >= rules.size()) continue;

					Rule rule = rules.get(index);
					hitCounts[index]++;
					renderedBlocks++;

					// 纯描边模式不再自绘线框
					if (rule.blockMode().wireframe()) drawBlock(bx, by, bz, rule.color, rule);
				}
			}
		}
		return renderedBlocks;
	}

	// ------------------------------------------------------- 相邻面剔除绘制

	private void drawBlock(int bx, int by, int bz, int color, Rule rule) {
		float pad = 0.002F;
		double x0 = bx - pad;
		double y0 = by - pad;
		double z0 = bz - pad;
		double x1 = bx + 1 + pad;
		double y1 = by + 1 + pad;
		double z1 = bz + 1 + pad;

		int mask = 0;
		if (rule.mergeAdjacent) {
			for (int i = 0; i < FACES.length; i++) {
				Direction dir = FACES[i];
				if (isHighlighted(bx + dir.getStepX(), by + dir.getStepY(), bz + dir.getStepZ())) {
					mask |= 1 << i;
				}
			}
		}

		if (mask == 0) {
			// 孤立方块：直接用一个 cuboid，比 12 条独立线更快
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, by, bz);
			GizmoProperties props = Gizmos.cuboid(pos, pad, GizmoStyle.stroke(color, rule.lineWidth));
			props.setAlwaysOnTop();
		} else {
			drawEdges(bx, by, bz, mask, color, rule, x0, y0, z0, x1, y1, z1);
		}
	}

	/**
	 * 按掩码只画外轮廓的棱。
	 * 除了「两侧都被遮住就不画」，还要处理共面：若 A 的某个暴露面与相邻块的同名面连成一片，
	 * 它们交界处的那条棱也不是外轮廓，必须一并去掉，否则一片平面方块仍会画成网格。
	 */
	private void drawEdges(int bx, int by, int bz, int mask, int color, Rule rule, double x0, double y0, double z0, double x1, double y1, double z1) {
		// 每条棱：[起止坐标(6 个 double)] + 两个所属面 + 对角方向
		edge(bx, by, bz, mask, color, rule, x0, y0, z0, x1, y0, z0, DOWN, NORTH);
		edge(bx, by, bz, mask, color, rule, x0, y0, z1, x1, y0, z1, DOWN, SOUTH);
		edge(bx, by, bz, mask, color, rule, x0, y0, z0, x0, y0, z1, DOWN, WEST);
		edge(bx, by, bz, mask, color, rule, x1, y0, z0, x1, y0, z1, DOWN, EAST);
		edge(bx, by, bz, mask, color, rule, x0, y1, z0, x1, y1, z0, UP, NORTH);
		edge(bx, by, bz, mask, color, rule, x0, y1, z1, x1, y1, z1, UP, SOUTH);
		edge(bx, by, bz, mask, color, rule, x0, y1, z0, x0, y1, z1, UP, WEST);
		edge(bx, by, bz, mask, color, rule, x1, y1, z0, x1, y1, z1, UP, EAST);
		edge(bx, by, bz, mask, color, rule, x0, y0, z0, x0, y1, z0, WEST, NORTH);
		edge(bx, by, bz, mask, color, rule, x1, y0, z0, x1, y1, z0, EAST, NORTH);
		edge(bx, by, bz, mask, color, rule, x0, y0, z1, x0, y1, z1, WEST, SOUTH);
		edge(bx, by, bz, mask, color, rule, x1, y0, z1, x1, y1, z1, EAST, SOUTH);
	}

	private static final int DOWN = 0;
	private static final int UP = 1;
	private static final int NORTH = 2;
	private static final int SOUTH = 3;
	private static final int WEST = 4;
	private static final int EAST = 5;

	private void edge(
		int bx, int by, int bz, int mask, int color, Rule rule,
		double ax, double ay, double az, double bxx, double byy, double bzz, int faceA, int faceB
	) {
		boolean naH = (mask & (1 << faceA)) != 0;
		boolean nbH = (mask & (1 << faceB)) != 0;
		boolean draw;
		if (!naH && !nbH) {
			draw = true; // 孤立棱：一定画
		} else {
			Direction a = FACES[faceA];
			Direction b = FACES[faceB];
			// 对角块高亮 == 「邻居在 faceA / faceB 方向上也还是实心」
			boolean diagH = isHighlighted(
				bx + a.getStepX() + b.getStepX(),
				by + a.getStepY() + b.getStepY(),
				bz + a.getStepZ() + b.getStepZ()
			);
			if (naH && nbH) {
				draw = !diagH; // 两面都遮住：只有拐角（对角是空的）才需要画
			} else {
				// 一面暴露、另一面被邻居遮住：若邻居的同名面也暴露（对角为空），
				// 说明两个面在同一平面上连成一片，这条棱不是外轮廓
				draw = diagH;
			}
		}
		if (!draw) return;
		GizmoProperties props = Gizmos.line(new Vec3(ax, ay, az), new Vec3(bxx, byy, bzz), color, rule.lineWidth);
		props.setAlwaysOnTop();
	}

	private boolean isHighlighted(int x, int y, int z) {
		long key = ChunkPos.pack(x >> 4, z >> 4);
		Map<Long, Long> map;
		if (key == lastChunkKey) map = lastChunkMap;
		else {
			map = chunks.get(key);
			lastChunkKey = key;
			lastChunkMap = map;
		}
		return map != null && map.containsKey(BlockPos.asLong(x, y, z));
	}
}
