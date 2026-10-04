package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 応答の貸出の状態。保存はせず、返却日時・返却期限・今日の日付から求める（延滞は日付が変わるだけで、書き込みは無い）。
 */
public enum LoanState {

	/** 未返却で、返却期限 ≥ 今日。 */
	LENT,
	/** 未返却で、返却期限 < 今日（延滞）。 */
	OVERDUE,
	/** 返却済み。 */
	RETURNED;

	static LoanState of(OffsetDateTime returnedAt, LocalDate dueDate, LocalDate today) {
		if (returnedAt != null) {
			return RETURNED;
		}
		return dueDate.isBefore(today) ? OVERDUE : LENT;
	}

}
