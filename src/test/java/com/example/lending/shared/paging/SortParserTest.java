package com.example.lending.shared.paging;

import com.example.lending.equipment.EquipmentSortKey;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import com.example.lending.shared.error.ProblemError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SortParserTest {

	private static final SortOrder<EquipmentSortKey> DEFAULT = SortOrder.asc(EquipmentSortKey.ASSET_CODE);

	private static SortOrder<EquipmentSortKey> parse(String value) {
		return SortParser.parse(value, EquipmentSortKey.class, DEFAULT);
	}

	@Test
	@DisplayName("キーと向き（name,desc）")
	void keyAndDirection() {
		assertThat(parse("name,desc")).isEqualTo(SortOrder.desc(EquipmentSortKey.NAME));
		assertThat(parse("name,asc")).isEqualTo(SortOrder.asc(EquipmentSortKey.NAME));
	}

	@Test
	@DisplayName("向きを省くと昇順")
	void keyOnlyIsAscending() {
		assertThat(parse("name")).isEqualTo(SortOrder.asc(EquipmentSortKey.NAME));
	}

	@Test
	@DisplayName("複数語のキーは camelCase（createdAt・dueDate・assetCode）")
	void camelCaseKeys() {
		assertThat(parse("createdAt,desc")).isEqualTo(SortOrder.desc(EquipmentSortKey.CREATED_AT));
		assertThat(parse("dueDate")).isEqualTo(SortOrder.asc(EquipmentSortKey.DUE_DATE));
		assertThat(parse("assetCode,desc")).isEqualTo(SortOrder.desc(EquipmentSortKey.ASSET_CODE));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  " })
	@DisplayName("省略すると既定の並び")
	void omittedMeansDefault(String value) {
		assertThat(parse(value)).isEqualTo(DEFAULT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "price", "name;DROP TABLE equipment", "ASSET_CODE", "Name", "name,desc,id", ",desc" })
	@DisplayName("AC-05 許可していない並び替えキーは 400 VALIDATION_FAILED（errors の parameter は sort）")
	void unknownKey_isRejected(String value) {
		assertRejected(value);
	}

	@ParameterizedTest
	@ValueSource(strings = { "name,down", "name,DESC", "name," })
	@DisplayName("知らない向きは 400")
	void unknownDirection_isRejected(String value) {
		assertRejected(value);
	}

	private static void assertRejected(String value) {
		BusinessException ex = catchThrowableOfType(BusinessException.class, () -> parse(value));
		assertThat(ex).isNotNull();
		assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(ex.errors()).extracting(ProblemError::parameter).containsExactly("sort");
	}

}
