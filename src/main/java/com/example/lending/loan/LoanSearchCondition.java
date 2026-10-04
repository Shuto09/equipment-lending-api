package com.example.lending.loan;

import java.util.Objects;

import com.example.lending.shared.paging.SortOrder;

/**
 * 貸出一覧の条件（コントローラで解釈した後の値）。borrowerId・equipmentId が null なら絞らない。
 * MEMBER の borrowerId は、サービスで本人に固定する（他人を指定すると 403）。
 */
public record LoanSearchCondition(LoanStateFilter state, Long borrowerId, Long equipmentId,
		SortOrder<LoanSortKey> sort) {

	public LoanSearchCondition {
		state = Objects.requireNonNullElse(state, LoanStateFilter.OPEN);
	}

}
