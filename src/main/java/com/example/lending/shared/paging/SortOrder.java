package com.example.lending.shared.paging;

/**
 * 並び順。キーは enum に限る（許可リスト）。マッパー XML は enum の名前を {@code <choose>} で列に対応させるので、
 * 利用者の入力が SQL の文字列に入る経路が無い。
 */
public record SortOrder<K extends Enum<K>>(K key, boolean descending) {

	public static <K extends Enum<K>> SortOrder<K> asc(K key) {
		return new SortOrder<>(key, false);
	}

	public static <K extends Enum<K>> SortOrder<K> desc(K key) {
		return new SortOrder<>(key, true);
	}

}
