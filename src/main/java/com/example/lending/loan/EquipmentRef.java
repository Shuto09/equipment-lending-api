package com.example.lending.loan;

/** 貸出の応答に載せる備品（id・管理番号・名前）。 */
public record EquipmentRef(long id, String assetCode, String name) {

}
