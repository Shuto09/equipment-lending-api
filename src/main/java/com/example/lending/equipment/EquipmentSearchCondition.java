package com.example.lending.equipment;

import java.util.List;
import java.util.Objects;

import com.example.lending.shared.paging.SortOrder;

/** 備品検索の条件（コントローラで検証・解釈した後の値）。categories が空なら区分で絞らない。 */
public record EquipmentSearchCondition(String keyword, List<EquipmentCategory> categories, Availability availability,
		SortOrder<EquipmentSortKey> sort) {

	public EquipmentSearchCondition {
		// category=LAPTOP&category= のような空の指定は null の要素になるので除く
		categories = categories == null ? List.of()
				: categories.stream().filter(Objects::nonNull).distinct().toList();
	}

}
