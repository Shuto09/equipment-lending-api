package com.example.lending.equipment;

import com.example.lending.shared.validation.TextPatterns;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 備品の部分更新（PATCH）。JSON Merge Patch ではなく、送った項目だけを変える独自の形。
 * 送らない項目と null は「変えない」。備考を消すときは空文字を送る。version は楽観ロックのために必須。
 * 名称・備考の検証は登録（{@link RegisterEquipmentRequest}）と同じ。
 */
public record UpdateEquipmentRequest(
		@Size(min = 1, max = 100) @Pattern(regexp = TextPatterns.NOT_BLANK, message = "空白だけの名称は使えません")
		@Pattern(regexp = TextPatterns.NO_CONTROL, message = "名称に改行・タブ・NUL などの制御文字は使えません") String name,
		EquipmentCategory category,
		@Size(max = 500) @Pattern(regexp = TextPatterns.NO_CONTROL_EXCEPT_LINE_BREAKS,
				message = "備考に改行とタブ以外の制御文字（NUL など）は使えません") String remarks,
		@NotNull Integer version) {

	/** version だけが上がる更新を作らない。 */
	@JsonIgnore
	@AssertTrue(message = "変更する項目（name・category・remarks）を 1 つ以上送ってください")
	public boolean isAnyFieldPresent() {
		return name != null || category != null || remarks != null;
	}

}
