package com.example.lending.equipment;

/** 備品検索の並び替えキーの許可リスト。API では camelCase（assetCode・createdAt・dueDate）で受ける。 */
public enum EquipmentSortKey {

	ASSET_CODE, NAME, CATEGORY, CREATED_AT, DUE_DATE

}
