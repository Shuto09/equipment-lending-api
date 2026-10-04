package com.example.lending.loan;

import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.SortOrder;

/** マッパーに渡す貸出一覧の値。borrowerId は権限で決めた後の値（MEMBER なら本人の id）。 */
public record LoanSearchQuery(LoanStateFilter state, Long borrowerId, Long equipmentId, SortOrder<LoanSortKey> sort,
		int limit, long offset) {

	static LoanSearchQuery of(LoanSearchCondition condition, Long borrowerId, PageQuery page) {
		return new LoanSearchQuery(condition.state(), borrowerId, condition.equipmentId(), condition.sort(),
				page.size(), page.offset());
	}

}
