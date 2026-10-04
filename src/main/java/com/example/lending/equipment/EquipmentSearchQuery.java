package com.example.lending.equipment;

import java.time.LocalDate;
import java.util.List;

import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.shared.sql.LikePatterns;

/**
 * マッパーに渡す検索の値。キーワードはエスケープ済みの LIKE パターンにし、今日の日付（延滞の判定）もここで渡す。
 */
public record EquipmentSearchQuery(String keywordPattern, List<EquipmentCategory> categories,
		Availability availability, SortOrder<EquipmentSortKey> sort, int limit, long offset, LocalDate today) {

	public static EquipmentSearchQuery of(EquipmentSearchCondition condition, PageQuery page, LocalDate today) {
		return new EquipmentSearchQuery(LikePatterns.containing(condition.keyword()), condition.categories(),
				condition.availability(), condition.sort(), page.size(), page.offset(), today);
	}

}
