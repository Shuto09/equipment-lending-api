package com.example.lending.equipment;

import java.time.OffsetDateTime;

/** 登録する備品。INSERT の後に MyBatis が生成された id を入れるので、ここだけ可変のクラスにする。 */
public class NewEquipment {

	private Long id;

	private final String assetCode;

	private final String name;

	private final EquipmentCategory category;

	private final String remarks;

	private final OffsetDateTime createdAt;

	public NewEquipment(String assetCode, String name, EquipmentCategory category, String remarks,
			OffsetDateTime createdAt) {
		this.assetCode = assetCode;
		this.name = name;
		this.category = category;
		this.remarks = remarks;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getAssetCode() {
		return assetCode;
	}

	public String getName() {
		return name;
	}

	public EquipmentCategory getCategory() {
		return category;
	}

	public String getRemarks() {
		return remarks;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
