package com.preciseglower.client.search;

import com.preciseglower.client.PreciseGlowerClient;
import com.preciseglower.client.config.EntityRule;
import com.preciseglower.client.config.ItemRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.match.EntityNbt;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 实体 / 掉落物追踪：每 tick 重建「实体 -> 命中规则」映射。
 * <ul>
 *     <li>类型支持通配符 {@code *}：{@code *{IsBaby:true}} 匹配所有幼年生物</li>
 *     <li>包围盒按 {@code Entity#getPosition(partialTick)} 插值，避免一卡一卡</li>
 *     <li>NBT 只在规则确实需要过滤时才会序列化</li>
 * </ul>
 */
public final class EntityTracker {
	/** 实体 -> 命中的规则 */
	private final Map<Entity, Rule> matched = new IdentityHashMap<>();
	/** 需要绘制包围盒的实体 */
	private final Map<Entity, Rule> boxes = new IdentityHashMap<>();

	private int[] entityCounts = new int[0];
	private int[] itemCounts = new int[0];

	private List<EntityRule> entityRules = new ArrayList<>();
	private List<ItemRule> itemRules = new ArrayList<>();
	/**
	 * 按优先级排好的候选：同一个掉落物可能同时被实体规则（比如 {@code *}）和物品规则命中，
	 * 两条列表不能各扫各的，必须放在一起比较优先级。
	 */
	private List<Rule> candidates = new ArrayList<>();
	private int maxDistance = 160;
	/** 粗过滤距离（格）：切比雪夫区块半径 R 最多对应 (R+1)*16 格，避免对远处实体做 NBT 序列化 */
	private int coarseRangeBlocks = 160;
	/** 所有规则里最大的水平区块半径 */
	private int maxRadiusChunks = 9;

	public void setRules(List<EntityRule> entityRules, List<ItemRule> itemRules) {
		this.entityRules = entityRules;
		this.itemRules = itemRules;
		this.entityCounts = new int[entityRules.size()];
		this.itemCounts = new int[itemRules.size()];
		// 物品规则恒优先于实体规则：哪怕实体侧显式写了 minecraft:item，也不能覆盖命中的物品规则。
		// 两条列表内部按用户排的顺序遍历（下标越小越优先），这里只做前后拼接，不再重排
		List<Rule> merged = new ArrayList<>(itemRules.size() + entityRules.size());
		merged.addAll(itemRules);
		merged.addAll(entityRules);
		this.candidates = merged;
		recomputeRange();
		clear();
	}

	public void setMaxDistance(int maxDistance) {
		this.maxDistance = Math.max(8, maxDistance);
		recomputeRange();
	}

	/**
	 * 全局上限仍是硬上限，规则的滑动条只在它之内再收窄。
	 *
	 * <p>范围是水平方向的切比雪夫区块距离：1 = 玩家所在区块（1×1），
	 * 2 = 3×3 区块，N = (2N-1)×(2N-1) 区块；垂直方向不限制。</p>
	 */
	private void recomputeRange() {
		int maxChunks = 1;
		for (EntityRule rule : entityRules) {
			maxChunks = Math.max(maxChunks, Math.min(globalRangeChunks(), rule.rangeChunks()));
		}
		for (ItemRule rule : itemRules) {
			maxChunks = Math.max(maxChunks, Math.min(globalRangeChunks(), rule.rangeChunks()));
		}
		maxRadiusChunks = maxChunks - 1;
		coarseRangeBlocks = Math.max(8, (maxRadiusChunks + 1) * 16);
	}

	private int globalRangeChunks() {
		return Math.max(1, maxDistance >> 4);
	}

	/** 水平区块切比雪夫距离，垂直不限 */
	private boolean withinRange(Entity entity, Rule rule, int pcx, int pcz) {
		int radius = Math.min(globalRangeChunks(), rule.rangeChunks()) - 1;
		int ecx = (int) Math.floor(entity.getX()) >> 4;
		int ecz = (int) Math.floor(entity.getZ()) >> 4;
		return Math.abs(ecx - pcx) <= radius && Math.abs(ecz - pcz) <= radius;
	}

	public void clear() {
		matched.clear();
		boxes.clear();
	}

	public int entityCount(int index) {
		return index >= 0 && index < entityCounts.length ? entityCounts[index] : 0;
	}

	public int itemCount(int index) {
		return index >= 0 && index < itemCounts.length ? itemCounts[index] : 0;
	}

	public int totalMatched() {
		return matched.size();
	}

	/** 当前匹配的规则（glow 判定用），未命中返回 null */
	public @Nullable Rule ruleFor(Entity entity) {
		return matched.get(entity);
	}

	/** 规则实际使用的颜色（自动取色只在添加规则时算一次初始值，之后就是普通颜色） */
	public int colorFor(Entity entity, Rule rule) {
		return rule == null ? 0xFFFFFFFF : rule.color;
	}

	public void onTick(ClientLevel level) {
		matched.clear();
		boxes.clear();
		if (level == null) return;
		if (entityRules.isEmpty() && itemRules.isEmpty()) return;

		entityCounts = new int[entityRules.size()];
		itemCounts = new int[itemRules.size()];
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Vec3 cam = mc.player.position();
		int pcx = (int) Math.floor(cam.x) >> 4;
		int pcz = (int) Math.floor(cam.z) >> 4;
		double coarseDistSqr = (double) coarseRangeBlocks * coarseRangeBlocks;
		HolderLookup.Provider registries = level.registryAccess();

		HideTracker hideTracker = PreciseGlowerClient.hideTracker();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == null || !entity.isAlive()) continue;
			double distSqr = entity.distanceToSqr(cam);
			if (distSqr > coarseDistSqr) continue;
			// 已经被隐藏规则屏蔽的实体不再发光 / 不再画包围盒（gizmo 是独立于实体渲染的）
			if (hideTracker != null && hideTracker.isHidden(entity)) continue;
			// 范围在 match 内部就过滤掉了：范围外的候选让位给优先级更低的候选
			Rule rule = match(entity, registries, pcx, pcz);
			if (rule == null) continue;
			matched.put(entity, rule);
			if (rule.showBox) boxes.put(entity, rule);
			if (rule instanceof EntityRule er) {
				int i = entityRules.indexOf(er);
				if (i >= 0) entityCounts[i]++;
			} else if (rule instanceof ItemRule ir) {
				int i = itemRules.indexOf(ir);
				if (i >= 0) itemCounts[i]++;
			}
		}
	}

	/**
	 * 按优先级依次试候选，取第一条「能命中 且 自己的范围覆盖该实体」的规则。
	 *
	 * <p>范围在这里而不是命中之后再判：否则一条范围很小的严格规则会把比它宽松、
	 * 范围更大的规则整条吃掉，而不是只在自己的范围内覆盖它。</p>
	 */
	private @Nullable Rule match(Entity entity, HolderLookup.Provider registries, int pcx, int pcz) {
		CompoundTag entityTag = null;
		CompoundTag stackTag = null;
		boolean itemEntity = entity instanceof ItemEntity;
		ItemStack stack = itemEntity ? ((ItemEntity) entity).getItem() : null;
		boolean itemVisible = stack != null && !stack.isEmpty();

		for (int i = 0; i < candidates.size(); i++) {
			Rule candidate = candidates.get(i);
			if (!candidate.enabled || candidate.error() != null) continue;
			if (!withinRange(entity, candidate, pcx, pcz)) continue;
			if (candidate instanceof EntityRule rule) {
				if (!rule.matchesType(entity)) continue;
				if (rule.matcher().isAny()) return rule;
				if (entityTag == null) entityTag = EntityNbt.of(entity, registries);
				if (entityTag != null && rule.matcher().matches(entityTag)) return rule;
			} else if (itemVisible) {
				ItemRule rule = (ItemRule) candidate;
				if (!rule.isWildcard() && rule.itemType() != stack.getItem()) continue;
				// 数量下限：达到指定数量才命中
				if (stack.getCount() < rule.minCount()) continue;
				if (rule.matcher().isAny()) return rule;
				if (stackTag == null) stackTag = EntityNbt.of(stack, registries);
				if (stackTag != null && rule.matcher().matches(stackTag)) return rule;
			}
		}
		return null;
	}

	/** 在 gizmo 收集窗口内绘制实体包围盒（按 partialTick 插值） */
	public void renderBoxes(float partialTick) {
		if (boxes.isEmpty()) return;
		for (Map.Entry<Entity, Rule> entry : boxes.entrySet()) {
			Entity entity = entry.getKey();
			Rule rule = entry.getValue();
			AABB box = entity.getBoundingBox();
			if (box == null) continue;

			// 原版 box 用的是当前 tick 坐标，这里平移到插值后的位置
			Vec3 lerped = entity.getPosition(partialTick);
			Vec3 now = entity.position();
			box = box.move(lerped.x - now.x, lerped.y - now.y, lerped.z - now.z);

			net.minecraft.gizmos.GizmoProperties props = net.minecraft.gizmos.Gizmos.cuboid(
				box,
				net.minecraft.gizmos.GizmoStyle.stroke(colorFor(entity, rule), rule.lineWidth)
			);
			props.setAlwaysOnTop();
		}
	}
}
