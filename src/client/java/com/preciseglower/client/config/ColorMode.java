package com.preciseglower.client.config;

/**
 * 规则取色方式。
 * <ul>
 *     <li>{@link #CUSTOM} —— 使用规则里手选的颜色</li>
 *     <li>{@link #AUTO} —— 自动取色：方块用地图色（{@code state.getMapColor()}），
 *         实体 / 掉落物用类型 id 的稳定哈希色（同一个 id 永远同一个颜色）</li>
 * </ul>
 */
public enum ColorMode {
	CUSTOM,
	AUTO
}
