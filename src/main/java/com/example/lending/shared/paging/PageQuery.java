package com.example.lending.shared.paging;

/**
 * 一覧の取得位置。page は 0 始まり。上限（page ≤ 10000・size ≤ 100）はコントローラの入力検証で守る。
 */
public record PageQuery(int page, int size) {

	/** OFFSET。page × size が int の範囲を超えないよう long で計算する。 */
	public long offset() {
		return (long) page * size;
	}

}
