package com.example.lending.loan;

/** 貸出一覧の絞り込み（クエリの state）。既定は OPEN。 */
public enum LoanStateFilter {

	/** 未返却だけ（延滞を含む）。 */
	OPEN,
	/** 返却済みだけ。 */
	RETURNED,
	/** すべて。 */
	ALL

}
