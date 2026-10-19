package com.preciseglower.client.gui.autocomplete;

/** 搜索结果条目：{@code id} 用于加入规则，{@code label} 是右侧灰色显示的本地化名。 */
public record Candidate(String id, String label) {
	public static Candidate of(String id) {
		return new Candidate(id, "");
	}

	public static Candidate of(String id, String label) {
		return new Candidate(id, label == null ? "" : label);
	}
}
