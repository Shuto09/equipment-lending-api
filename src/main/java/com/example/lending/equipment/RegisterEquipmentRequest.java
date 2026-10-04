package com.example.lending.equipment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 備品の登録。備考の空文字は「備考なし」として NULL で保存する。 */
public record RegisterEquipmentRequest(
		@NotNull @Pattern(regexp = "^[A-Z]{2,4}-[0-9]{4}$",
				message = "管理番号は英大文字 2〜4 字・ハイフン・数字 4 桁の形で指定してください（例 PC-0001）") String assetCode,
		@NotNull @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "空白だけの名称は使えません") String name,
		@NotNull EquipmentCategory category, @Size(max = 500) String remarks) {

}
