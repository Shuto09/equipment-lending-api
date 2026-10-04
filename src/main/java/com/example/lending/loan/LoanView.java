package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 貸出＋備品の管理番号と名前＋借り手の表示名（一覧・詳細の 1 行）。returnedAt が null なら未返却。 */
public record LoanView(long id, long equipmentId, String assetCode, String equipmentName, long borrowerId,
		String borrowerName, OffsetDateTime lentAt, LocalDate dueDate, OffsetDateTime returnedAt) {

}
