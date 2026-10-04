package com.example.lending;

import java.util.function.Consumer;
import java.util.stream.Stream;

import com.example.lending.equipment.EquipmentCategory;
import com.example.lending.shared.sql.DbConstraints;
import com.example.lending.support.MapperDbTest;
import com.example.lending.support.TestData;
import com.example.lending.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static com.example.lending.support.TestData.BASE;
import static com.example.lending.support.TestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * DB の制約が効いていることを、制約違反 1 種につき 1 回のテストで確かめる（docs/テスト計画.md §4-2）。
 * PostgreSQL は制約違反の後の文を受け付けない（25P02）ので、1 つのメソッドで続けて確かめない。
 * 例外の型だけでなく、DbConstraints で取り出した制約名まで確かめる（業務エラーへの変換が制約名に頼っているため）。
 */
@MapperDbTest
class SchemaConstraintTest {

	@Autowired
	private TestData data;

	private long equipmentId;

	private long userId;

	@BeforeEach
	void rows() {
		data.deleteAll();
		userId = data.yamada();
		equipmentId = data.equipment("PC-0001", "ノート PC", EquipmentCategory.LAPTOP);
	}

	static Stream<Arguments> violations() {
		return Stream.of(arguments(DbConstraints.UQ_LOANS_OPEN_EQUIPMENT, (Violation) t -> {
			t.data.openLoan(t.equipmentId, t.userId, TODAY);
			t.data.openLoan(t.equipmentId, t.userId, TODAY);
		}), arguments(DbConstraints.UQ_EQUIPMENT_ASSET_CODE,
				(Violation) t -> t.data.equipment("PC-0001", "別の PC", EquipmentCategory.LAPTOP)),
				arguments(DbConstraints.UQ_USERS_EMAIL,
						(Violation) t -> t.data.user("山田 二郎", "yamada@example.com", Role.MEMBER)),
				arguments(DbConstraints.CK_USERS_ROLE, (Violation) t -> t.sql(
						"INSERT INTO users (display_name, email, role) VALUES ('佐藤 花子', 'sato@example.com', 'GUEST')")),
				arguments(DbConstraints.CK_EQUIPMENT_CATEGORY, (Violation) t -> t.sql("""
						INSERT INTO equipment (asset_code, name, category, created_at)
						VALUES ('PH-0001', 'スマートフォン', 'PHONE', '2026-10-05 10:00+09')""")),
				arguments(DbConstraints.CK_EQUIPMENT_STATUS,
						(Violation) t -> t.sql("UPDATE equipment SET status = 'BROKEN' WHERE asset_code = 'PC-0001'")),
				arguments(DbConstraints.CK_EQUIPMENT_DISPOSED,
						(Violation) t -> t.sql("UPDATE equipment SET status = 'DISPOSED' WHERE asset_code = 'PC-0001'")),
				arguments(DbConstraints.CK_EQUIPMENT_DISPOSED, (Violation) t -> t.sql(
						"UPDATE equipment SET disposed_at = '2026-10-05 10:00+09' WHERE asset_code = 'PC-0001'")),
				arguments(DbConstraints.CK_EQUIPMENT_REMARKS_NOT_EMPTY,
						(Violation) t -> t.sql("UPDATE equipment SET remarks = '' WHERE asset_code = 'PC-0001'")),
				arguments(DbConstraints.CK_LOANS_RETURNED_AFTER_LENT, (Violation) t -> t.data
					.returnedLoan(t.equipmentId, t.userId, TODAY, BASE.minusDays(30))));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("violations")
	@DisplayName("制約違反は DataIntegrityViolationException になり、制約名を取り出せる")
	void violation_isDetectedWithConstraintName(String constraint, Violation violation) {
		DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> violation.accept(this));

		assertThat(ex).as("制約違反の例外が投げられること").isNotNull();
		assertThat(DbConstraints.violatedConstraint(ex)).contains(constraint);
	}

	@Test
	@DisplayName("同じ備品でも、未返却 1 件＋返却済みは何件でも入る（一意なのは未返却の行だけ）")
	void oneOpenLoanAndManyReturnedLoans_areAllowed() {
		data.returnedLoan(equipmentId, userId, TODAY, BASE);
		data.returnedLoan(equipmentId, userId, TODAY, BASE);
		data.openLoan(equipmentId, userId, TODAY);

		assertThat(data.jdbc()
			.sql("SELECT count(*) FROM loans WHERE equipment_id = ?")
			.param(equipmentId)
			.query(Long.class)
			.single()).isEqualTo(3);
	}

	/** 制約違反を起こす操作。テストの行（id）と TestData を受け取る。 */
	interface Violation extends Consumer<SchemaConstraintTest> {

	}

	private void sql(String statement) {
		data.jdbc().sql(statement).update();
	}

}
