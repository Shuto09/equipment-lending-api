package com.example.lending.equipment;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 備品＋貸出状態＋今の借り手（検索・詳細の 1 行）。借り手の列は未返却の貸出が無ければ null。
 */
public record EquipmentView(long id, String assetCode, String name, EquipmentCategory category, String remarks,
		Availability availability, int version, OffsetDateTime createdAt, OffsetDateTime disposedAt, Long borrowerId,
		String borrowerName, LocalDate dueDate) {

}
