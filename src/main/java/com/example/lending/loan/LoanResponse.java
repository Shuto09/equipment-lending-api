package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.example.lending.shared.config.LendingProperties;
import com.example.lending.user.UserSummary;

/**
 * 貸出（docs/要件と設計.md §6）。日時は業務のタイムゾーン（日本時間）のオフセットで返す。
 *
 * @param state 今日の日付で求めた状態（LENT・OVERDUE・RETURNED）
 * @param returnedLate 返却日（日本時間）が返却期限より後なら true。未返却なら false
 */
public record LoanResponse(long id, EquipmentRef equipment, UserSummary borrower, OffsetDateTime lentAt,
		LocalDate dueDate, OffsetDateTime returnedAt, LoanState state, boolean returnedLate) {

	static LoanResponse from(LoanView view, LocalDate today, LendingProperties lending) {
		OffsetDateTime returnedAt = lending.toZone(view.returnedAt());
		// 日付は日本時間で比べる（UTC のままだと、日本時間の 0:00〜9:00 の返却が前日の扱いになる）
		boolean returnedLate = returnedAt != null && returnedAt.toLocalDate().isAfter(view.dueDate());
		return new LoanResponse(view.id(), new EquipmentRef(view.equipmentId(), view.assetCode(), view.equipmentName()),
				new UserSummary(view.borrowerId(), view.borrowerName()), lending.toZone(view.lentAt()), view.dueDate(),
				returnedAt, LoanState.of(view.returnedAt(), view.dueDate(), today), returnedLate);
	}

}
