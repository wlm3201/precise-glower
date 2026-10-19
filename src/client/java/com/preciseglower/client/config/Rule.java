package com.preciseglower.client.config;

import com.preciseglower.client.util.Colors;

/** 所有高亮规则的公共字段。 */
public abstract class Rule {
	/** 是否启用 */
	public boolean enabled = true;
	/** 高亮颜色，0xAARRGGBB */
	public int color = 0xFF00FF80;
	/** 线框宽度（gizmo stroke width） */
	public float lineWidth = 2.0F;
	/** 穿墙显示：默认恒为 true（不再提供开关） */
	public boolean alwaysOnTop = true;
	/** 实体/掉落物：使用原版发光轮廓 */
	public boolean glow = true;
	/** 实体/掉落物：额外绘制包围盒线框 */
	public boolean showBox = false;
	/** 取色方式（保留字段兼容旧配置；现在的行为是：添加规则时用自动色作为初始颜色，之后随意手改） */
	public ColorMode colorMode = ColorMode.AUTO;
	/** 方块：合并相邻面（恒为开，不再提供开关） */
	public boolean mergeAdjacent = true;
	/** 方块：线框 / 描边 / 两者都要 */
	public BlockMode blockMode = BlockMode.WIREFRAME;
	/** 该规则的高亮范围（区块） */
	public int rangeChunks = DEFAULT_RANGE_CHUNKS;
	/**
	 * 不透明度 0-100：只对隐藏规则生效。
	 * 0 = 完全不渲染（原本的「隐藏」），100 = 原样渲染，中间值是半透明。
	 */
	public int opacity = 0;
	/** 忽略交互：准心 / 攻击不再选中被隐藏规则命中的实体（只对隐藏规则生效） */
	public boolean ignoreInteraction = true;

	/** 归一化后的不透明度：0 = 完全隐藏；1 = 这条规则不改透明度 */
	public float opacity01() {
		if (opacity <= 0) return 0F;
		if (opacity >= 100) return 1F;
		return opacity / 100F;
	}

	/** 绘制方式（旧配置里没有这个字段时退回线框） */
	public BlockMode blockMode() {
		return blockMode == null ? BlockMode.WIREFRAME : blockMode;
	}

	// ------------------------------------------------------------ 高亮范围

	public static final int MIN_RANGE_CHUNKS = 1;
	public static final int MAX_RANGE_CHUNKS = 16;
	public static final int DEFAULT_RANGE_CHUNKS = 16;

	/**
	 * 归一化后的范围（区块）：旧配置没有这个字段、或值异常时退回默认。
	 *
	 * <p>语义是水平方向的切比雪夫区块距离：1 = 玩家所在区块（1×1），
	 * 2 = 3×3 区块，N = (2N-1)×(2N-1) 区块；垂直方向不限制。</p>
	 */
	public int rangeChunks() {
		if (rangeChunks < MIN_RANGE_CHUNKS) return DEFAULT_RANGE_CHUNKS;
		return Math.min(rangeChunks, MAX_RANGE_CHUNKS);
	}

	public void setRangeChunks(int chunks) {
		rangeChunks = Math.max(MIN_RANGE_CHUNKS, Math.min(chunks, MAX_RANGE_CHUNKS));
	}

	/**
	 * 该规则的匹配条件标识（id / NBT 的组合）。
	 *
	 * <p>注意它<strong>不是唯一键</strong>：同一个 id 允许存在多条规则，靠方块状态 / NBT 区分。</p>
	 */
	public abstract String key();

	/** 列表中显示的名称 */
	public abstract String displayName();

	/** 过滤表达式（方块状态 / NBT），为空表示不过滤 */
	public abstract String filterText();

	/** 最近一次解析的错误信息，null 表示正常 */
	public abstract String error();

	/** 重新解析过滤表达式，需要在主线程且注册表可用时调用 */
	public abstract void compile();

	/** 供列表显示的过滤摘要 */
	public String filterSummary() {
		String text = filterText();
		return text == null || text.isBlank() ? "" : text;
	}

	/**
	 * 笼统程度评分（越大越具体）：具体 id 比通配符高一级，带过滤表达式（方块状态 / NBT）再高一级。
	 *
	 * <p>于是共四级：{@code *} &lt; {@code *[state]} &lt; {@code id} &lt; {@code id[state]}。
	 * 它现在只用于决定「新添加的规则落在列表哪一档」——真正的优先级是列表下标
	 * （见 {@link GlowerConfig}），用户可以随便用上下箭头改。</p>
	 *
	 * <p>注意优先级只决定「谁覆盖谁」：每条规则仍然在自己的 {@link #rangeChunks()} 之外失效，
	 * 被它压住的宽松规则在那里照常生效。</p>
	 */
	public int specificity() {
		int score = isWildcard() ? 0 : 2;
		String filter = filterText();
		if (filter != null && !filter.isBlank()) score++;
		return score;
	}

	public int opaqueColor() {
		return color | 0xFF000000;
	}

	/** 该规则是否匹配任意种类（通配符 {@code *}） */
	public boolean isWildcard() {
		return false;
	}

	public static int defaultColor(int index) {
		return Colors.PRESET[Math.floorMod(index, Colors.PRESET.length)];
	}

	public Rule copy() {
		Rule clone = newInstance();
		clone.enabled = this.enabled;
		clone.color = this.color;
		clone.lineWidth = this.lineWidth;
		clone.alwaysOnTop = this.alwaysOnTop;
		clone.glow = this.glow;
		clone.showBox = this.showBox;
		clone.colorMode = this.colorMode;
		clone.mergeAdjacent = this.mergeAdjacent;
		clone.blockMode = this.blockMode;
		clone.rangeChunks = this.rangeChunks;
		clone.opacity = this.opacity;
		clone.ignoreInteraction = this.ignoreInteraction;
		copyInto(clone);
		return clone;
	}

	protected abstract Rule newInstance();

	protected abstract void copyInto(Rule target);
}
