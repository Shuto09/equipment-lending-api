package com.example.lending.equipment;

import com.example.lending.shared.validation.TextPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 備品の登録。備考の空文字は「備考なし」として NULL で保存する。 */
public record RegisterEquipmentRequest(
		@NotNull @Pattern(regexp = "^[A-Z]{2,4}-[0-9]{4}$",
				message = "管理番号は英大文字 2〜4 字・ハイフン・数字 4 桁の形で指定してください（例 PC-0001）") String assetCode,
		@NotNull @Size(min = 1, max = 100) @Pattern(regexp = TextPatterns.NOT_BLANK, message = "空白だけの名称は使えません")
		@Pattern(regexp = TextPatterns.NO_CONTROL, message = "名称に改行・タブ・NUL などの制御文字は使えません") String name,
		@NotNull EquipmentCategory category,
		@Size(max = 500) @Pattern(regexp = TextPatterns.NO_CONTROL_EXCEPT_LINE_BREAKS,
				message = "備考に改行とタブ以外の制御文字（NUL など）は使えません") String remarks) {

}
