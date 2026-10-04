package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.example.lending.equipment.EquipmentCategory;
import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.support.MapperDbTest;
import com.example.lending.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static com.example.lending.support.TestData.BASE;
import static com.example.lending.support.TestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 貸出の SQL を本物の PostgreSQL で確かめる（延滞の判定・延滞一覧の並び・条件付きの返却・一覧の動的 SQL）。
 * 各テストはロールバックされる。空の DB を前提にせず、先に 3 表を空にしてから行を入れる。
 */
@MapperDbTest
class LoanMapperTest {

	@Autowired
	private LoanMapper mapper;

	@Autowired
	private TestData data;

	private long yamada;

	private long sato;

	@BeforeEach
	void clean() {
		data.deleteAll();
		yamada = data.yamada();
		sato = data.sato();
	}

	private long equipment(String assetCode) {
		return data.equipment(assetCode, "備品 " + assetCode, EquipmentCategory.OTHER);
	}

	@Test
	@DisplayName("登録で生成された id を受け取り、備品の管理番号・借り手の表示名と一緒に読める。OffsetDateTime は往復しても同じ時刻")
	void insert_roundTrip() {
		long pc = data.equipment("PC-0007", "ノート PC 14 インチ", EquipmentCategory.LAPTOP);
		OffsetDateTime lentAt = OffsetDateTime.parse("2026-10-05T10:15:00.123456+09:00");
		NewLoan loan = new NewLoan(pc, yamada, lentAt, TODAY.plusDays(7));

		mapper.insert(loan);

		assertThat(loan.getId()).isNotNull();
		LoanView view = mapper.findView(loan.getId()).orElseThrow();
		assertThat(view).returns(pc, LoanView::equipmentId)
			.returns("PC-0007", LoanView::assetCode)
			.returns("ノート PC 14 インチ", LoanView::equipmentName)
			.returns(yamada, LoanView::borrowerId)
			.returns("山田 太郎", LoanView::borrowerName)
			.returns(TODAY.plusDays(7), LoanView::dueDate)
			.returns(null, LoanView::returnedAt);
		assertThat(view.lentAt()).isAtSameInstantAs(lentAt);
		assertThat(mapper.findView(loan.getId() + 1000)).isEmpty();
	}

	@Test
	@DisplayName("利用者ごとの未返却と延滞の件数: 期限が今日は延滞でない・昨日は延滞。返却済みと他人の貸出は数えない")
	void statsByBorrower() {
		data.openLoan(equipment("OTH-0001"), yamada, TODAY); // 未返却・期限が今日（延滞でない）
		data.openLoan(equipment("OTH-0002"), yamada, TODAY.minusDays(1)); // 未返却・延滞
		data.returnedLoan(equipment("OTH-0003"), yamada, TODAY.minusDays(5), BASE.minusDays(1)); // 返却済み（期限後）
		data.openLoan(equipment("OTH-0004"), sato, TODAY.minusDays(3)); // 他人の延滞

		assertThat(mapper.statsByBorrower(yamada, TODAY)).isEqualTo(new BorrowerLoanStats(2, 1));
		assertThat(mapper.statsByBorrower(sato, TODAY)).isEqualTo(new BorrowerLoanStats(1, 1));
		assertThat(mapper.statsByBorrower(data.admin(), TODAY)).isEqualTo(new BorrowerLoanStats(0, 0));
		// 今日はアプリから渡す: 1 日前に戻せば、昨日が期限の貸出はまだ延滞でない
		assertThat(mapper.statsByBorrower(yamada, TODAY.minusDays(1))).isEqualTo(new BorrowerLoanStats(2, 0));
	}

	@Test
	@DisplayName("AC-12 返却の条件付き UPDATE: 1 回目は 1 件、2 回目は 0 件（最初の返却日時は上書きしない）")
	void markReturned_onlyOnce() {
		long loan = data.openLoan(equipment("OTH-0001"), yamada, TODAY);

		assertThat(mapper.markReturned(loan, BASE)).isEqualTo(1);
		assertThat(mapper.markReturned(loan, BASE.plusHours(1))).isZero();

		assertThat(mapper.findView(loan).orElseThrow().returnedAt()).isAtSameInstantAs(BASE);
	}

	@Nested
	@DisplayName("AC-13 延滞一覧")
	class Overdue {

		private long olderFirst;

		private long olderSecond;

		private long yesterday;

		@BeforeEach
		void rows() {
			yesterday = data.openLoan(equipment("OTH-0001"), yamada, TODAY.minusDays(1));
			olderFirst = data.openLoan(equipment("OTH-0002"), sato, TODAY.minusDays(3));
			olderSecond = data.openLoan(equipment("OTH-0003"), yamada, TODAY.minusDays(3)); // 期限が同じなら id 順
			data.openLoan(equipment("OTH-0004"), sato, TODAY); // 期限が今日: 延滞でない
			data.openLoan(equipment("OTH-0005"), sato, TODAY.plusDays(3)); // 期限内
			data.returnedLoan(equipment("OTH-0006"), yamada, TODAY.minusDays(10), BASE.minusDays(2)); // 返却済み
		}

		@Test
		@DisplayName("期限切れの未返却だけを、延滞日数の多い順（期限・id の順）に返す。延滞日数と借り手のメールアドレスが付く")
		void overdue_onlyOpenPastDue_mostOverdueFirst() {
			List<OverdueLoanView> rows = mapper.findOverdue(TODAY, 100, 0);

			assertThat(rows)
				.extracting(OverdueLoanView::id, OverdueLoanView::overdueDays, OverdueLoanView::borrowerEmail)
				.containsExactly(tuple(olderFirst, 3, "sato@example.com"), tuple(olderSecond, 3, "yamada@example.com"),
						tuple(yesterday, 1, "yamada@example.com"));
			assertThat(rows.getFirst()).returns("OTH-0002", OverdueLoanView::assetCode)
				.returns("佐藤 花子", OverdueLoanView::borrowerName);
			assertThat(mapper.countOverdue(TODAY)).isEqualTo(3);
		}

		@Test
		@DisplayName("ページング: 件数はページの位置で変わらず、範囲外のページは空")
		void overdue_paging() {
			assertThat(mapper.findOverdue(TODAY, 2, 2)).extracting(OverdueLoanView::id).containsExactly(yesterday);
			assertThat(mapper.findOverdue(TODAY, 2, 4)).isEmpty();
			assertThat(mapper.countOverdue(TODAY)).isEqualTo(3);
		}

		@Test
		@DisplayName("日付が進むと延滞になる（保存せず、渡された今日の日付で毎回求める）")
		void overdue_dependsOnToday() {
			LocalDate later = TODAY.plusDays(1);

			assertThat(mapper.findOverdue(later, 100, 0)).extracting(OverdueLoanView::overdueDays)
				.containsExactly(4, 4, 2, 1);
			assertThat(mapper.countOverdue(later)).isEqualTo(4);
		}

	}

	@Nested
	@DisplayName("AC-21 貸出一覧")
	class Search {

		private long yamadaOpen;

		private long yamadaReturned;

		private long satoOpen;

		private long satoReturned;

		private long pc1;

		@BeforeEach
		void rows() {
			pc1 = equipment("PC-0001");
			long pc2 = equipment("PC-0002");
			// 貸出日時・期限の順がそれぞれ違うように入れる
			yamadaReturned = data.loan(pc1, yamada, BASE.minusDays(20), TODAY.minusDays(10), BASE.minusDays(12));
			satoOpen = data.loan(pc1, sato, BASE.minusDays(2), TODAY.plusDays(1), null);
			yamadaOpen = data.loan(pc2, yamada, BASE.minusDays(5), TODAY.plusDays(9), null);
			satoReturned = data.loan(equipment("PC-0003"), sato, BASE.minusDays(9), TODAY.minusDays(4),
					BASE.minusDays(6));
		}

		private List<Long> ids(LoanStateFilter state, Long borrowerId, Long equipmentId, SortOrder<LoanSortKey> sort) {
			return mapper.search(query(state, borrowerId, equipmentId, sort, new PageQuery(0, 100)))
				.stream()
				.map(LoanView::id)
				.toList();
		}

		private List<Long> ids(LoanStateFilter state, Long borrowerId, Long equipmentId) {
			return ids(state, borrowerId, equipmentId, SortOrder.desc(LoanSortKey.LENT_AT));
		}

		private LoanSearchQuery query(LoanStateFilter state, Long borrowerId, Long equipmentId,
				SortOrder<LoanSortKey> sort, PageQuery page) {
			return LoanSearchQuery.of(new LoanSearchCondition(state, borrowerId, equipmentId, sort), borrowerId, page);
		}

		@Test
		@DisplayName("状態: OPEN は未返却だけ・RETURNED は返却済みだけ・ALL はすべて（既定の並びは貸出日時の降順）")
		void byState() {
			assertThat(ids(LoanStateFilter.OPEN, null, null)).containsExactly(satoOpen, yamadaOpen);
			assertThat(ids(LoanStateFilter.RETURNED, null, null)).containsExactly(satoReturned, yamadaReturned);
			assertThat(ids(LoanStateFilter.ALL, null, null)).containsExactly(satoOpen, yamadaOpen, satoReturned,
					yamadaReturned);
		}

		@Test
		@DisplayName("借り手・備品・状態の組み合わせ")
		void combined() {
			assertThat(ids(LoanStateFilter.ALL, yamada, null)).containsExactly(yamadaOpen, yamadaReturned);
			assertThat(ids(LoanStateFilter.OPEN, yamada, null)).containsExactly(yamadaOpen);
			assertThat(ids(LoanStateFilter.ALL, null, pc1)).containsExactly(satoOpen, yamadaReturned);
			assertThat(ids(LoanStateFilter.RETURNED, yamada, pc1)).containsExactly(yamadaReturned);
			assertThat(ids(LoanStateFilter.OPEN, sato, pc1)).containsExactly(satoOpen);
			assertThat(ids(LoanStateFilter.OPEN, yamada, pc1)).isEmpty();
		}

		@Test
		@DisplayName("並び替え: 返却期限の昇順・降順、貸出日時の昇順")
		void sorting() {
			assertThat(ids(LoanStateFilter.ALL, null, null, SortOrder.asc(LoanSortKey.DUE_DATE)))
				.containsExactly(yamadaReturned, satoReturned, satoOpen, yamadaOpen);
			assertThat(ids(LoanStateFilter.ALL, null, null, SortOrder.desc(LoanSortKey.DUE_DATE)))
				.containsExactly(yamadaOpen, satoOpen, satoReturned, yamadaReturned);
			assertThat(ids(LoanStateFilter.ALL, null, null, SortOrder.asc(LoanSortKey.LENT_AT)))
				.containsExactly(yamadaReturned, satoReturned, yamadaOpen, satoOpen);
		}

		@Test
		@DisplayName("貸出日時が同じ行は id 順（ページの境目で行が重複・欠落しない）")
		void sameLentAt_orderedById() {
			long first = data.loan(equipment("PC-0004"), sato, BASE, TODAY, null);
			long second = data.loan(equipment("PC-0005"), sato, BASE, TODAY, null);

			assertThat(ids(LoanStateFilter.OPEN, sato, null)).containsExactly(first, second, satoOpen);
		}

		@Test
		@DisplayName("ページング: 件数の文と一覧の文の条件が一致し、範囲外のページは空")
		void paging() {
			SortOrder<LoanSortKey> sort = SortOrder.desc(LoanSortKey.LENT_AT);
			assertThat(mapper.search(query(LoanStateFilter.ALL, null, null, sort, new PageQuery(1, 3))))
				.extracting(LoanView::id)
				.containsExactly(yamadaReturned);
			assertThat(mapper.search(query(LoanStateFilter.ALL, null, null, sort, new PageQuery(2, 3)))).isEmpty();
			for (LoanStateFilter state : LoanStateFilter.values()) {
				for (Long borrowerId : new Long[] { null, yamada }) {
					LoanSearchQuery query = query(state, borrowerId, null, sort, new PageQuery(0, 100));
					assertThat(mapper.count(query)).as(state + " " + borrowerId).isEqualTo(mapper.search(query).size());
				}
			}
		}

	}

}
