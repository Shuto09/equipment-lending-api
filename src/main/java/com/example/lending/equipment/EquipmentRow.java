package com.example.lending.equipment;

import java.time.OffsetDateTime;

/** equipment の 1 行（更新・廃棄の判定に使う）。 */
public record EquipmentRow(long id, String assetCode, String name, EquipmentCategory category, String remarks,
		EquipmentStatus status, OffsetDateTime disposedAt, int version, OffsetDateTime createdAt) {

}
