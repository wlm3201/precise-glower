package com.preciseglower.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局配置；规则列表与渲染参数。
 *
 * <p>三个「高亮」列表（{@link #blocks} / {@link #entities} / {@link #items}）的
 * <strong>下标就是生效优先级</strong>：越小越优先。界面里那条 List 与这里存的是同一份实例，
 * 上下箭头换的也就是它的元素位置，追踪器按下标决定「谁压住谁」；所以任何地方都不要替用户重排。
 * 隐藏列表不在此列：命中即隐藏，不分优先级。</p>
 */
public final class GlowerConfig {
	public List<BlockRule> blocks = new ArrayList<>();
	public List<EntityRule> entities = new ArrayList<>();
	public List<ItemRule> items = new ArrayList<>();

	/** 隐藏渲染的实体规则：类型（可通配）+ NBT */
	public List<EntityRule> hiddenEntities = new ArrayList<>();
	/** 隐藏渲染的物品规则：物品 id + 可选 NBT（作用于地上的掉落物） */
	public List<ItemRule> hiddenItems = new ArrayList<>();

	/**
	 * 方块扫描 / 渲染半径（格）：256 = 16 区块，正好放开规则滑条的上限。
	 *
	 * <p>它是硬上限：每规则的范围还要跟它取 min，这个值一小，滑条拉满也不会变远。</p>
	 */
	public int blockMaxDistance = 256;
	/** 实体匹配半径（格）：同理，256 = 16 区块 */
	public int entityMaxDistance = 256;
	/** 每 tick 最多扫描的区块数 */
	public int scanBudgetPerTick = 4;
	/** 单帧最多提交的方块线框数 */
	public int maxRenderedBlocks = 8000;
	/** 记住上次停留的标签页（BLOCK / ENTITY / ITEM） */
	public String lastTab = "BLOCK";

	/** 规则内容发生变化，需要重新解析 */
	public transient boolean dirty = true;

	public void markDirty() {
		dirty = true;
	}

	/** 重新解析所有规则的过滤表达式（必须在注册表可用后调用） */
	public void compileAll() {
		for (BlockRule rule : blocks) rule.compile();
		for (EntityRule rule : entities) rule.compile();
		for (ItemRule rule : items) rule.compile();
		for (EntityRule rule : hiddenEntities) rule.compile();
		for (ItemRule rule : hiddenItems) rule.compile();
		dirty = false;
	}

	// ------------------------------------------------------------ 范围上限

	/**
	 * 每规则范围还要再被全局上限收窄一次（格 -&gt; 区块）。
	 *
	 * <p>追踪器里就是这个值在跟 {@code rangeChunks()} 取 min，界面上要照它显示实际能到几格。</p>
	 */
	public int blockRangeCapChunks() {
		return Math.max(1, blockMaxDistance >> 4);
	}

	/** 实体 / 掉落物侧的全局上限（区块） */
	public int entityRangeCapChunks() {
		return Math.max(1, entityMaxDistance >> 4);
	}

	public boolean hasAnyEnabled() {
		for (BlockRule r : blocks) if (r.enabled) return true;
		for (EntityRule r : entities) if (r.enabled) return true;
		for (ItemRule r : items) if (r.enabled) return true;
		for (EntityRule r : hiddenEntities) if (r.enabled) return true;
		for (ItemRule r : hiddenItems) if (r.enabled) return true;
		return false;
	}
}
