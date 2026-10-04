package com.example.lending.equipment;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.example.lending.shared.config.LendingProperties;
import com.example.lending.user.UserSummary;

/**
 * 備品の詳細。貸出中なら今の借り手（表示名）と返却期限を全員に見せる（社内の台帳。docs/要件と設計.md §6）。
 * 日時は業務のタイムゾーン（日本時間）のオフセットで返す。
 */
public record EquipmentResponse(long id, String assetCode, String name, EquipmentCategory category, String remarks,
		Availability availability, CurrentLoan currentLoan, int version, OffsetDateTime createdAt,
		OffsetDateTime disposedAt) {

	static EquipmentResponse from(EquipmentView view, LendingProperties lending) {
		return new EquipmentResponse(view.id(), view.assetCode(), view.name(), view.category(), view.remarks(),
				view.availability(), CurrentLoan.from(view), view.version(), lending.toZone(view.createdAt()),
				lending.toZone(view.disposedAt()));
	}

	/** 今の貸出（未返却）。借り手は表示名まで（メールアドレスは出さない）。 */
	public record CurrentLoan(UserSummary borrower, LocalDate dueDate) {

		static CurrentLoan from(EquipmentView view) {
			if (view.borrowerId() == null) {
				return null;
			}
			return new CurrentLoan(new UserSummary(view.borrowerId(), view.borrowerName()), view.dueDate());
		}

	}

}
