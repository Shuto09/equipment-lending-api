package com.example.lending.equipment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 備品の部分更新（PATCH）。JSON Merge Patch ではなく、送った項目だけを変える独自の形。
 * 送らない項目と null は「変えない」。備考を消すときは空文字を送る。version は楽観ロックのために必須。
 */
public record UpdateEquipmentRequest(
		@Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "空白だけの名称は使えません") String name,
		EquipmentCategory category, @Size(max = 500) String remarks, @NotNull Integer version) {

	/** version だけが上がる更新を作らない。 */
	@JsonIgnore
	@AssertTrue(message = "変更する項目（name・category・remarks）を 1 つ以上送ってください")
	public boolean isAnyFieldPresent() {
		return name != null || category != null || remarks != null;
	}

}
