package com.example.lending.shared.sql;

/**
 * キーワードを LIKE / ILIKE の部分一致のパターンにする。{@code %}・{@code _}・{@code \} は文字として扱う
 * （「100%」で「100」から始まる全件が当たらないように）。SQL 側は {@code ESCAPE '\'} を付けて使う。
 */
public final class LikePatterns {

	private LikePatterns() {
	}

	/** 部分一致のパターン。空・空白だけなら null（絞り込みなし）。 */
	public static String containing(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return "%" + escape(keyword.strip()) + "%";
	}

	static String escape(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
