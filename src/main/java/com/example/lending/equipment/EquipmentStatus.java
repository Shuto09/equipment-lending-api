package com.example.lending.equipment;

/** 保存する備品の状態。「貸出中」は保存せず、貸出の表から求める（{@link Availability}）。 */
public enum EquipmentStatus {

	ACTIVE, DISPOSED

}
