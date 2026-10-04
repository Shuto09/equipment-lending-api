package com.example.lending.equipment;

/**
 * 備品の部分更新（楽観ロック）。name・category が null なら変えない。
 * 備考は「消す（NULL にする）」と「変えない」を分けるため、remarksChanged で書くかどうかを持つ。
 */
public record EquipmentUpdate(long id, int version, String name, EquipmentCategory category, boolean remarksChanged,
		String remarks) {

}
