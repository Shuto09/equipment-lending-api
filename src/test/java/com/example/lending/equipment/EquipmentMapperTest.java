package com.example.lending.equipment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.support.MapperDbTest;
import com.example.lending.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;

import static com.example.lending.support.TestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 備品の SQL を本物の PostgreSQL で確かめる（動的 SQL の条件の組み合わせ・並び順・ページング・状態の導出・楽観ロック）。
 * 各テストはロールバックされる。空の DB を前提にせず、先に 3 表を空にしてから行を入れる。
 */
@MapperDbTest
class EquipmentMapperTest {

	@Autowired
	private EquipmentMapper mapper;

	@Autowired
	private TestData data;

	private long yamada;

	@BeforeEach
	void clean() {
		data.deleteAll();
		yamada = data.yamada();
	}

	private List<String> assetCodes(String keyword, List<EquipmentCategory> categories, Availability availability,
			SortOrder<EquipmentSortKey> sort) {
		EquipmentSearchCondition condition = new EquipmentSearchCondition(keyword, categories, availability, sort);
		return mapper.search(EquipmentSearchQuery.of(condition, new PageQuery(0, 100), TODAY))
			.stream()
			.map(EquipmentView::assetCode)
			.toList();
	}

	private List<String> assetCodes(String keyword) {
		return assetCodes(keyword, List.of(), null, SortOrder.asc(EquipmentSortKey.ASSET_CODE));
	}

	@Nested
	@DisplayName("AC-04 キーワード")
	class Keyword {

		@BeforeEach
		void rows() {
			data.equipment("PC-0001", "Laptop 14", EquipmentCategory.LAPTOP);
			data.equipment("MON-0001", "モニター 27 インチ", EquipmentCategory.MONITOR);
			data.equipment("OTH-0001", "100%充電器", EquipmentCategory.OTHER);
			data.equipment("OTH-0002", "1000 円の延長コード", EquipmentCategory.OTHER);
			data.equipment("OTH-0003", "a_b ケーブル", EquipmentCategory.OTHER);
			data.equipment("OTH-0004", "axb ケーブル", EquipmentCategory.OTHER);
		}

		@Test
		@DisplayName("名前・管理番号の部分一致で、大文字小文字を区別しない")
		void partialMatch_caseInsensitive() {
			assertThat(assetCodes("LAP")).containsExactly("PC-0001");
			assertThat(assetCodes("mon-")).containsExactly("MON-0001");
			assertThat(assetCodes("インチ")).containsExactly("MON-0001");
		}

		@Test
		@DisplayName("% と _ は文字として扱う（「100%」で「1000 円」は当たらない）")
		void wildcardsAreLiteral() {
			assertThat(assetCodes("100%")).containsExactly("OTH-0001");
			assertThat(assetCodes("a_b")).containsExactly("OTH-0003");
		}

		@Test
		@DisplayName("' OR '1'='1 は 0 件で、表は無事")
		void injectionAttempt_isJustAString() {
			assertThat(assetCodes("' OR '1'='1")).isEmpty();
			assertThat(assetCodes(null)).hasSize(6);
		}

	}

	@Nested
	@DisplayName("AC-04 区分と貸出状態")
	class Filters {

		@BeforeEach
		void rows() {
			data.equipment("PC-0001", "Laptop A", EquipmentCategory.LAPTOP);
			long lent = data.equipment("PC-0002", "Laptop B", EquipmentCategory.LAPTOP);
			long overdue = data.equipment("MON-0001", "Monitor A", EquipmentCategory.MONITOR);
			data.equipment("PJ-0001", "Projector A", EquipmentCategory.PROJECTOR);
			data.disposedEquipment("OTH-0001", "Other A", EquipmentCategory.OTHER);
			data.openLoan(lent, yamada, TODAY); // 期限が今日 → LENT
			data.openLoan(overdue, yamada, TODAY.minusDays(1)); // 期限が昨日 → OVERDUE
			long returned = data.equipment("PJ-0002", "Projector B", EquipmentCategory.PROJECTOR);
			data.returnedLoan(returned, yamada, TODAY.minusDays(3), TestData.BASE.minusDays(4)); // 返却済み → AVAILABLE
		}

		@Test
		@DisplayName("区分: 1 つ・複数・空のリストは絞らない")
		void categories() {
			SortOrder<EquipmentSortKey> sort = SortOrder.asc(EquipmentSortKey.ASSET_CODE);
			assertThat(assetCodes(null, List.of(EquipmentCategory.LAPTOP), null, sort)).containsExactly("PC-0001",
					"PC-0002");
			assertThat(assetCodes(null, List.of(EquipmentCategory.MONITOR, EquipmentCategory.PROJECTOR), null, sort))
				.containsExactly("MON-0001", "PJ-0001", "PJ-0002");
			assertThat(assetCodes(null, List.of(), null, sort)).hasSize(5);
		}

		@Test
		@DisplayName("貸出状態: 4 つの値。期限が今日なら LENT、昨日なら OVERDUE。返却済みの貸出は数えない")
		void availability() {
			SortOrder<EquipmentSortKey> sort = SortOrder.asc(EquipmentSortKey.ASSET_CODE);
			assertThat(assetCodes(null, List.of(), Availability.AVAILABLE, sort)).containsExactly("PC-0001", "PJ-0001",
					"PJ-0002");
			assertThat(assetCodes(null, List.of(), Availability.LENT, sort)).containsExactly("PC-0002");
			assertThat(assetCodes(null, List.of(), Availability.OVERDUE, sort)).containsExactly("MON-0001");
			assertThat(assetCodes(null, List.of(), Availability.DISPOSED, sort)).containsExactly("OTH-0001");
		}

		@Test
		@DisplayName("貸出状態を省くと廃棄済みを除く")
		void defaultExcludesDisposed() {
			assertThat(assetCodes(null)).containsExactly("MON-0001", "PC-0001", "PC-0002", "PJ-0001", "PJ-0002");
		}

		@Test
		@DisplayName("条件の組み合わせ（キーワード＋区分＋貸出状態）")
		void combined() {
			assertThat(assetCodes("laptop", List.of(EquipmentCategory.LAPTOP), Availability.LENT,
					SortOrder.asc(EquipmentSortKey.ASSET_CODE)))
				.containsExactly("PC-0002");
		}

		@Test
		@DisplayName("貸出中の行に借り手の表示名と返却期限が付く")
		void lentRowHasBorrower() {
			EquipmentView view = mapper
				.search(EquipmentSearchQuery.of(new EquipmentSearchCondition(null, List.of(), Availability.LENT,
						SortOrder.asc(EquipmentSortKey.ASSET_CODE)), new PageQuery(0, 20), TODAY))
				.getFirst();
			assertThat(view.borrowerId()).isEqualTo(yamada);
			assertThat(view.borrowerName()).isEqualTo("山田 太郎");
			assertThat(view.dueDate()).isEqualTo(TODAY);
		}

	}

	@Nested
	@DisplayName("AC-04 並び替え")
	class Sorting {

		private long b1;

		private long b2;

		@BeforeEach
		void rows() {
			// 名前・区分・登録日時・返却期限がそれぞれ違う順になるように入れる。
			// b1 と b2 は名前・区分・登録日時が同じなので、その 3 つのキーでは id 順に並ぶ
			data.equipment("PC-0003", "Charlie", EquipmentCategory.LAPTOP, TestData.BASE.minusDays(1));
			b1 = data.equipment("MON-0002", "Bravo", EquipmentCategory.MONITOR, TestData.BASE.minusDays(3));
			b2 = data.equipment("MON-0001", "Bravo", EquipmentCategory.MONITOR, TestData.BASE.minusDays(3));
			long alpha = data.equipment("PJ-0001", "Alpha", EquipmentCategory.PROJECTOR, TestData.BASE.minusDays(2));
			data.openLoan(alpha, yamada, TODAY.plusDays(1));
			data.openLoan(b2, data.sato(), TODAY.plusDays(5));
		}

		private List<String> sorted(EquipmentSortKey key, boolean descending) {
			return assetCodes(null, List.of(), null, new SortOrder<>(key, descending));
		}

		@Test
		@DisplayName("名前（同じ名前は id 順）")
		void byName() {
			assertThat(sorted(EquipmentSortKey.NAME, false)).containsExactly("PJ-0001", "MON-0002", "MON-0001",
					"PC-0003");
			assertThat(sorted(EquipmentSortKey.NAME, true)).containsExactly("PC-0003", "MON-0002", "MON-0001",
					"PJ-0001");
			assertThat(b1).isLessThan(b2);
		}

		@Test
		@DisplayName("管理番号・区分・登録日時")
		void byOtherKeys() {
			assertThat(sorted(EquipmentSortKey.ASSET_CODE, false)).containsExactly("MON-0001", "MON-0002", "PC-0003",
					"PJ-0001");
			assertThat(sorted(EquipmentSortKey.ASSET_CODE, true)).containsExactly("PJ-0001", "PC-0003", "MON-0002",
					"MON-0001");
			assertThat(sorted(EquipmentSortKey.CATEGORY, false)).containsExactly("PC-0003", "MON-0002", "MON-0001",
					"PJ-0001");
			assertThat(sorted(EquipmentSortKey.CATEGORY, true)).containsExactly("PJ-0001", "MON-0002", "MON-0001",
					"PC-0003");
			assertThat(sorted(EquipmentSortKey.CREATED_AT, false)).containsExactly("MON-0002", "MON-0001", "PJ-0001",
					"PC-0003");
			assertThat(sorted(EquipmentSortKey.CREATED_AT, true)).containsExactly("PC-0003", "PJ-0001", "MON-0002",
					"MON-0001");
		}

		@Test
		@DisplayName("返却期限: 期限の無い（貸出中でない）行は昇順でも降順でも最後")
		void byDueDate_nullsLast() {
			// 期限の無い 2 行（PC-0003・MON-0002）は id 順
			assertThat(sorted(EquipmentSortKey.DUE_DATE, false)).containsExactly("PJ-0001", "MON-0001", "PC-0003",
					"MON-0002");
			assertThat(sorted(EquipmentSortKey.DUE_DATE, true)).containsExactly("MON-0001", "PJ-0001", "PC-0003",
					"MON-0002");
		}

	}

	@Nested
	@DisplayName("AC-04 ページング")
	class Paging {

		@BeforeEach
		void rows() {
			for (int i = 1; i <= 5; i++) {
				data.equipment("PC-000" + i, "Laptop " + i, EquipmentCategory.LAPTOP);
			}
			data.disposedEquipment("PC-0009", "Laptop 9", EquipmentCategory.LAPTOP);
		}

		private List<String> page(int page, int size) {
			EquipmentSearchCondition condition = new EquipmentSearchCondition(null, List.of(), null,
					SortOrder.asc(EquipmentSortKey.ASSET_CODE));
			return mapper.search(EquipmentSearchQuery.of(condition, new PageQuery(page, size), TODAY))
				.stream()
				.map(EquipmentView::assetCode)
				.toList();
		}

		@Test
		@DisplayName("0 ページ目・最後のページ・範囲外のページ（空）")
		void pages() {
			assertThat(page(0, 2)).containsExactly("PC-0001", "PC-0002");
			assertThat(page(2, 2)).containsExactly("PC-0005");
			assertThat(page(3, 2)).isEmpty();
		}

		@Test
		@DisplayName("件数の文と一覧の文の条件が一致する（ページの位置で件数は変わらない）")
		void countMatchesSearch() {
			Function<Availability, EquipmentSearchQuery> query = availability -> EquipmentSearchQuery
				.of(new EquipmentSearchCondition(null, List.of(), availability, SortOrder.asc(EquipmentSortKey.NAME)),
						new PageQuery(0, 100), TODAY);
			for (Availability availability : new Availability[] { null, Availability.AVAILABLE,
					Availability.DISPOSED, Availability.LENT }) {
				assertThat(mapper.count(query.apply(availability))).as(String.valueOf(availability))
					.isEqualTo(mapper.search(query.apply(availability)).size());
			}
			EquipmentSearchQuery outOfRange = EquipmentSearchQuery.of(new EquipmentSearchCondition(null, List.of(),
					null, SortOrder.asc(EquipmentSortKey.NAME)), new PageQuery(10000, 100), TODAY);
			assertThat(mapper.count(outOfRange)).isEqualTo(5);
			assertThat(mapper.search(outOfRange)).isEmpty();
		}

	}

	@Nested
	@DisplayName("AC-15・AC-23 楽観ロックの更新")
	class UpdateWithVersion {

		private long id;

		@BeforeEach
		void row() {
			id = data.equipment("PC-0001", "Laptop", EquipmentCategory.LAPTOP);
			data.jdbc().sql("UPDATE equipment SET remarks = '元の備考' WHERE id = ?").param(id).update();
		}

		@Test
		@DisplayName("version が合えば 1 件で、version が 1 増える。送らなかった列は変わらない")
		void matchingVersion_updatesSentColumnsOnly() {
			int updated = mapper.updateWithVersion(new EquipmentUpdate(id, 0, "新しい名前", null, false, null));

			assertThat(updated).isEqualTo(1);
			EquipmentRow row = mapper.findById(id).orElseThrow();
			assertThat(row.name()).isEqualTo("新しい名前");
			assertThat(row.category()).isEqualTo(EquipmentCategory.LAPTOP);
			assertThat(row.remarks()).isEqualTo("元の備考");
			assertThat(row.version()).isEqualTo(1);
		}

		@Test
		@DisplayName("備考を消す（NULL）と、区分だけを変える")
		void clearRemarks_andChangeCategory() {
			assertThat(mapper.updateWithVersion(new EquipmentUpdate(id, 0, null, EquipmentCategory.OTHER, true, null)))
				.isEqualTo(1);

			EquipmentRow row = mapper.findById(id).orElseThrow();
			assertThat(row.remarks()).isNull();
			assertThat(row.category()).isEqualTo(EquipmentCategory.OTHER);
			assertThat(row.name()).isEqualTo("Laptop");
		}

		@Test
		@DisplayName("古い version は 0 件（上書きしない）")
		void staleVersion_updatesNothing() {
			mapper.updateWithVersion(new EquipmentUpdate(id, 0, "1 人目の変更", null, false, null));

			assertThat(mapper.updateWithVersion(new EquipmentUpdate(id, 0, "2 人目の変更", null, false, null)))
				.isZero();
			assertThat(mapper.findById(id).orElseThrow().name()).isEqualTo("1 人目の変更");
		}

		@Test
		@DisplayName("廃棄済みは 0 件")
		void disposed_updatesNothing() {
			mapper.markDisposed(id, TestData.BASE);

			assertThat(mapper.updateWithVersion(new EquipmentUpdate(id, 1, "名前", null, false, null))).isZero();
		}

	}

	@Test
	@DisplayName("登録で生成された id を受け取り、OffsetDateTime は往復しても同じ時刻")
	void insert_roundTrip() {
		OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-05T10:00:00.123456+09:00");
		NewEquipment equipment = new NewEquipment("PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null, createdAt);

		mapper.insert(equipment);

		assertThat(equipment.getId()).isNotNull();
		EquipmentRow row = mapper.findById(equipment.getId()).orElseThrow();
		assertThat(row.createdAt()).isAtSameInstantAs(createdAt);
		assertThat(row.status()).isEqualTo(EquipmentStatus.ACTIVE);
		assertThat(row.version()).isZero();
		assertThat(mapper.lockById(equipment.getId())).contains(row);
	}

	@Test
	@DisplayName("廃棄: 状態・廃棄日時・version が変わり、詳細の貸出状態は DISPOSED")
	void markDisposed() {
		long id = data.equipment("PC-0001", "ノート PC", EquipmentCategory.LAPTOP);

		assertThat(mapper.markDisposed(id, TestData.BASE)).isEqualTo(1);

		EquipmentRow row = mapper.findById(id).orElseThrow();
		assertThat(row.status()).isEqualTo(EquipmentStatus.DISPOSED);
		assertThat(row.disposedAt()).isAtSameInstantAs(TestData.BASE);
		assertThat(row.version()).isEqualTo(1);
		assertThat(mapper.findView(id, TODAY).orElseThrow().availability()).isEqualTo(Availability.DISPOSED);
	}

	@Test
	@DisplayName("未返却の有無（返却済みの貸出は数えない）")
	void hasOpenLoan() {
		long open = data.equipment("PC-0001", "ノート PC", EquipmentCategory.LAPTOP);
		long returned = data.equipment("PC-0002", "ノート PC", EquipmentCategory.LAPTOP);
		data.openLoan(open, yamada, TODAY);
		data.returnedLoan(returned, yamada, TODAY, TestData.BASE);

		assertThat(mapper.hasOpenLoan(open)).isTrue();
		assertThat(mapper.hasOpenLoan(returned)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Availability.class, names = { "LENT", "OVERDUE" })
	@DisplayName("詳細: 期限が今日なら LENT、昨日なら OVERDUE（今日はアプリから渡す）")
	void findView_derivesAvailabilityFromToday(Availability expected) {
		long id = data.equipment("PC-0001", "ノート PC", EquipmentCategory.LAPTOP);
		LocalDate due = expected == Availability.LENT ? TODAY : TODAY.minusDays(1);
		data.openLoan(id, yamada, due);

		EquipmentView view = mapper.findView(id, TODAY).orElseThrow();

		assertThat(view.availability()).isEqualTo(expected);
		assertThat(view.borrowerName()).isEqualTo("山田 太郎");
		assertThat(mapper.findView(id + 1000, TODAY)).isEmpty();
	}

}
