package com.example.lending.shared.paging;

import java.util.List;
import java.util.function.Function;

/** 一覧の応答の形: {@code { "items": [...], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }}。 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

	public static <R, T> PageResult<T> of(List<R> rows, Function<R, T> mapper, PageQuery query, long totalElements) {
		int totalPages = (int) ((totalElements + query.size() - 1) / query.size());
		return new PageResult<>(rows.stream().map(mapper).toList(), query.page(), query.size(), totalElements,
				totalPages);
	}

}
