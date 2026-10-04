package com.example.lending.equipment;

import java.time.OffsetDateTime;

import com.example.lending.equipment.EquipmentResponse.CurrentLoan;
import com.example.lending.shared.config.LendingProperties;

/** 備品検索の 1 件。 */
public record EquipmentSummaryResponse(long id, String assetCode, String name, EquipmentCategory category,
		Availability availability, CurrentLoan currentLoan, OffsetDateTime createdAt) {

	static EquipmentSummaryResponse from(EquipmentView view, LendingProperties lending) {
		return new EquipmentSummaryResponse(view.id(), view.assetCode(), view.name(), view.category(),
				view.availability(), CurrentLoan.from(view), lending.toZone(view.createdAt()));
	}

}
