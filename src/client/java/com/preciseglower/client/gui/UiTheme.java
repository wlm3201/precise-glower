package com.preciseglower.client.gui;

/**
 * 界面配色与尺寸常量（单位：GUI 像素）。
 * 想手调颜色就改这个文件 —— 所有界面颜色都集中在这。
 *
 * <p>配色思路：整个界面就是一块 {@link #MASK} 半透明黑遮罩铺满全屏，
 * 上面用 {@link #LINE} / {@link #WHITE} 画线和文字，再用 {@link #ACCENT} 点缀。
 * 同值不同名的旧常量（SURFACE / INPUT / LIST_BG…）已合并。</p>
 */
public final class UiTheme {
	// 布局（640x360）：无独立顶栏，tab 栏（y = 0 起）在最上方
	public static final int TAB_H = 18;
	/** 每个 tab 的宽度：{@link #SIDE_W} 取 3 个 tab，竖直分割线正好落在「掉落物 / 隐藏实体」之间 */
	public static final int TAB_W = 84;
	public static final int SIDE_W = TAB_W * 3;
	/** 17 = 14px 按钮（与 CONTROL_H 一致）+ 与上下两条分隔线各 1px 间距 */
	public static final int LIST_HEADER_H = 17;
	/** 23 = 20px 按钮 + 上下各 1px 呼吸位（outline 向内画，边框贴着分割线会糊成一条） */
	public static final int ROW_H = 23;
	public static final int CONTROL_H = 14;

	// 弹窗（RuleEditModal）
	public static final int MODAL_W = 330;
	public static final int MODAL_HEAD_H = 16;
	/** 弹窗左右内边距 */
	public static final int MODAL_PAD = 8;
	public static final int MODAL_BTN_H = 18;
	/** 一条「标签 + 输入框」占的高度（标签 10 + 输入框 CONTROL_H） */
	public static final int FIELD_STEP = 10 + CONTROL_H;
	/** 一条滑块 / 一个开关占的行高（含行距） */
	public static final int SLIDER_ROW_H = 12;
	public static final int TOGGLE_H = 13;

	// 色板
	/** 全屏遮罩 / 面板 / 列表 / 弹窗 / 输入框 / 关态色块 —— 整块界面的底色 */
	public static final int MASK = 0x80000000;
	/** 半透明白：边框 / 分隔线 / 滚动条轨道 / 弱化文字 */
	public static final int LINE = 0x80FFFFFF;
	/** 纯白：文字 / 聚焦边框 / 滚动条滑块 / 棋盘格亮格 */
	public static final int WHITE = 0xFFFFFFFF;
	/** 纯黑：颜色预览里的深色文字 / 棋盘格暗格 */
	public static final int BLACK = 0xFF000000;
	/** 强调色（选中 / 聚焦 / 开关的「开」状态） */
	public static final int ACCENT = 0xFF00FFC0;
	/** 半透明强调：选中态底色 */
	public static final int ACCENT_SOFT = 0x8000FFC0;
	/** 半透明红：关闭按钮悬停底色 */
	public static final int DANGER = 0x80FF0000;
	/** 纯红：危险按钮悬停 / 边框 / 文字、R 通道滑块 */
	public static final int DANGER_SOLID = 0xFFFF0000;
	public static final int WARN = 0xFFFF8000;
	/** RGB 通道滑块的强调色 */
	public static final int CHANNEL_G = 0xFF00FF00;
	public static final int CHANNEL_B = 0xFF0000FF;

	private UiTheme() {
	}
}
