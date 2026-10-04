package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.example.lending.shared.config.LendingProperties;

/**
 * 延滞一覧の 1 件（ADMIN だけ）。催促の連絡のため、ここにだけ借り手のメールアドレスを出す（docs/要件と設計.md §6）。
 *
 * @param overdueDays 延滞日数（今日 − 返却期限。期限の翌日が 1）
 */
public record OverdueLoanResponse(long id, EquipmentRef equipment, BorrowerContact borrower, OffsetDateTime lentAt,
		LocalDate dueDate, int overdueDays) {

	static OverdueLoanResponse from(OverdueLoanView view, LendingProperties lending) {
		return new OverdueLoanResponse(view.id(),
				new EquipmentRef(view.equipmentId(), view.assetCode(), view.equipmentName()),
				new BorrowerContact(view.borrowerId(), view.borrowerName(), view.borrowerEmail()),
				lending.toZone(view.lentAt()), view.dueDate(), view.overdueDays());
	}

	/** 借り手と連絡先。 */
	public record BorrowerContact(long id, String displayName, String email) {

	}

}
