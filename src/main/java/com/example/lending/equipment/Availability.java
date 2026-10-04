package com.example.lending.equipment;

/**
 * API が返す貸出状態。保存はせず、備品の status と未返却の貸出・今日の日付から SQL の CASE 式 1 か所で求める
 * （mapper/equipment/EquipmentMapper.xml の equipmentWithAvailability）。
 */
public enum Availability {

	/** ACTIVE で、未返却の貸出が無い。 */
	AVAILABLE,
	/** ACTIVE で、未返却の貸出があり、返却期限 ≥ 今日。 */
	LENT,
	/** ACTIVE で、未返却の貸出があり、返却期限 < 今日。 */
	OVERDUE,
	/** 廃棄済み。 */
	DISPOSED

}
