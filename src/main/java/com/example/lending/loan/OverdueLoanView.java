package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 延滞一覧の 1 行。延滞日数（今日 − 返却期限）は SQL で求める。連絡のため借り手のメールアドレスを含む（ADMIN だけが見る）。 */
public record OverdueLoanView(long id, long equipmentId, String assetCode, String equipmentName, long borrowerId,
		String borrowerName, String borrowerEmail, OffsetDateTime lentAt, LocalDate dueDate, int overdueDays) {

}
