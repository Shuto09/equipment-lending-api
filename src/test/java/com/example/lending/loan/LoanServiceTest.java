package com.example.lending.loan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.lending.equipment.EquipmentCategory;
import com.example.lending.equipment.EquipmentMapper;
import com.example.lending.equipment.EquipmentRow;
import com.example.lending.equipment.EquipmentStatus;
import com.example.lending.shared.config.LendingProperties;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.PageResult;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.shared.sql.DbConstraints;
import com.example.lending.user.CurrentUser;
import com.example.lending.user.Role;
import com.example.lending.user.UserMapper;
import com.example.lending.user.UserRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DuplicateKeyException;

import static com.example.lending.support.PostgresErrors.duplicateKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 貸出の業務ルール（docs/要件と設計.md §4）の全分岐を、マッパーを差し替えて確かめる。 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

	/** 2026-10-05 10:00 日本時間（UTC では 01:00）。 */
	private static final Instant NOW = Instant.parse("2026-10-05T01:00:00Z");

	private static final OffsetDateTime NOW_TOKYO = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	private static final LendingProperties LENDING = new LendingProperties(TOKYO, 14, 3);

	private static final CurrentUser ADMIN = new CurrentUser(1, Role.ADMIN);

	private static final CurrentUser YAMADA = new CurrentUser(2, Role.MEMBER);

	private static final CurrentUser SATO = new CurrentUser(3, Role.MEMBER);

	private static final long EQUIPMENT_ID = 7;

	private static final long LOAN_ID = 42;

	@Mock
	private LoanMapper loanMapper;

	@Mock
	private EquipmentMapper equipmentMapper;

	@Mock
	private UserMapper userMapper;

	private LoanService service;

	@BeforeEach
	void setUp() {
		service = service(Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private LoanService service(Clock clock) {
		return new LoanService(loanMapper, equipmentMapper, userMapper, clock, LENDING);
	}

	@Nested
	class Lend {

		private static LendRequest request(LocalDate dueDate) {
			return new LendRequest(EQUIPMENT_ID, dueDate);
		}

		/** 借りられる状態: 利用者がいて、備品は ACTIVE で空いていて、未返却は openLoans 件・延滞なし。 */
		private void lendable(long openLoans, LocalDate today) {
			given(userMapper.lockById(YAMADA.id())).willReturn(Optional.of(yamadaRow()));
			given(equipmentMapper.lockById(EQUIPMENT_ID)).willReturn(Optional.of(equipment(EquipmentStatus.ACTIVE)));
			given(equipmentMapper.hasOpenLoan(EQUIPMENT_ID)).willReturn(false);
			given(loanMapper.statsByBorrower(YAMADA.id(), today)).willReturn(new BorrowerLoanStats(openLoans, 0));
		}

		private void insertAssignsIdAndViewReturns(LocalDate dueDate) {
			willAnswer(invocation -> {
				invocation.<NewLoan>getArgument(0).setId(LOAN_ID);
				return null;
			}).given(loanMapper).insert(any());
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.of(openView(YAMADA.id(), dueDate)));
		}

		@Test
		@DisplayName("AC-06 空いている備品を借りられる。貸出日時は Clock の時刻、借り手は要求を出した本人")
		void lend_availableEquipment_succeeds() {
			lendable(0, TODAY);
			insertAssignsIdAndViewReturns(TODAY.plusDays(7));

			LoanResponse response = service.lend(YAMADA, request(TODAY.plusDays(7)));

			ArgumentCaptor<NewLoan> inserted = ArgumentCaptor.forClass(NewLoan.class);
			verify(loanMapper).insert(inserted.capture());
			assertThat(inserted.getValue()).satisfies(loan -> {
				assertThat(loan.getEquipmentId()).isEqualTo(EQUIPMENT_ID);
				assertThat(loan.getBorrowerId()).isEqualTo(YAMADA.id());
				assertThat(loan.getLentAt()).isEqualTo(NOW_TOKYO);
				assertThat(loan.getDueDate()).isEqualTo(TODAY.plusDays(7));
			});
			assertThat(response.id()).isEqualTo(LOAN_ID);
			assertThat(response.state()).isEqualTo(LoanState.LENT);
			assertThat(response.returnedLate()).isFalse();
		}

		@Test
		@DisplayName("ロックの順番は「利用者 → 備品」で、判定はロックの後（デッドロックしない順番に固定）")
		void lend_locksUserThenEquipment() {
			lendable(0, TODAY);
			insertAssignsIdAndViewReturns(TODAY);

			service.lend(YAMADA, request(TODAY));

			InOrder order = inOrder(userMapper, equipmentMapper, loanMapper);
			order.verify(userMapper).lockById(YAMADA.id());
			order.verify(equipmentMapper).lockById(EQUIPMENT_ID);
			order.verify(equipmentMapper).hasOpenLoan(EQUIPMENT_ID);
			order.verify(loanMapper).statsByBorrower(YAMADA.id(), TODAY);
			order.verify(loanMapper).insert(any());
		}

		@ParameterizedTest(name = "今日から {0} 日後 → 通るか: {1}")
		@CsvSource({ "0, true", "14, true", "-1, false", "15, false" })
		@DisplayName("AC-11 返却期限は今日〜14 日後（両端を含む）。外れたら 422 で、ロックの前に弾く（マッパーを呼ばない）")
		void lend_dueDateBoundaries(int days, boolean accepted) {
			LocalDate dueDate = TODAY.plusDays(days);
			if (accepted) {
				lendable(0, TODAY);
				insertAssignsIdAndViewReturns(dueDate);
				assertThat(service.lend(YAMADA, request(dueDate)).dueDate()).isEqualTo(dueDate);
				return;
			}
			assertCode(() -> service.lend(YAMADA, request(dueDate)), ErrorCode.DUE_DATE_OUT_OF_RANGE);
			verifyNoInteractions(userMapper, equipmentMapper, loanMapper);
		}

		@Test
		@DisplayName("無い備品は 404 EQUIPMENT_NOT_FOUND")
		void lend_equipmentNotFound() {
			given(userMapper.lockById(YAMADA.id())).willReturn(Optional.of(yamadaRow()));
			given(equipmentMapper.lockById(EQUIPMENT_ID)).willReturn(Optional.empty());

			assertCode(() -> service.lend(YAMADA, request(TODAY)), ErrorCode.EQUIPMENT_NOT_FOUND);
			verify(loanMapper, never()).insert(any());
		}

		@Test
		@DisplayName("AC-08 廃棄済みの備品は 409 EQUIPMENT_DISPOSED")
		void lend_disposed_conflict() {
			given(userMapper.lockById(YAMADA.id())).willReturn(Optional.of(yamadaRow()));
			given(equipmentMapper.lockById(EQUIPMENT_ID)).willReturn(Optional.of(equipment(EquipmentStatus.DISPOSED)));

			assertCode(() -> service.lend(YAMADA, request(TODAY)), ErrorCode.EQUIPMENT_DISPOSED);
			verify(loanMapper, never()).insert(any());
		}

		@Test
		@DisplayName("AC-07 貸出中の備品は 409 EQUIPMENT_ALREADY_LENT")
		void lend_alreadyLent_conflict() {
			given(userMapper.lockById(YAMADA.id())).willReturn(Optional.of(yamadaRow()));
			given(equipmentMapper.lockById(EQUIPMENT_ID)).willReturn(Optional.of(equipment(EquipmentStatus.ACTIVE)));
			given(equipmentMapper.hasOpenLoan(EQUIPMENT_ID)).willReturn(true);

			assertCode(() -> service.lend(YAMADA, request(TODAY)), ErrorCode.EQUIPMENT_ALREADY_LENT);
			verify(loanMapper, never()).insert(any());
		}

		@Test
		@DisplayName("AC-09 延滞中の貸出がある利用者は 422 BORROWER_HAS_OVERDUE")
		void lend_borrowerHasOverdue_rejected() {
			given(userMapper.lockById(YAMADA.id())).willReturn(Optional.of(yamadaRow()));
			given(equipmentMapper.lockById(EQUIPMENT_ID)).willReturn(Optional.of(equipment(EquipmentStatus.ACTIVE)));
			given(equipmentMapper.hasOpenLoan(EQUIPMENT_ID)).willReturn(false);
			given(loanMapper.statsByBorrower(YAMADA.id(), TODAY)).willReturn(new BorrowerLoanStats(1, 1));

			assertCode(() -> service.lend(YAMADA, request(TODAY)), ErrorCode.BORROWER_HAS_OVERDUE);
			verify(loanMapper, never()).insert(any());
		}

		@Test
		@DisplayName("AC-10 未返却 2 件なら借りられ、3 件（上限）なら 422 LOAN_LIMIT_EXCEEDED")
		void lend_atLimit_rejected() {
			lendable(2, TODAY);
			insertAssignsIdAndViewReturns(TODAY);
			assertThat(service.lend(YAMADA, request(TODAY)).id()).isEqualTo(LOAN_ID);

			given(loanMapper.statsByBorrower(YAMADA.id(), TODAY)).willReturn(new BorrowerLoanStats(3, 0));
			assertCode(() -> service.lend(YAMADA, request(TODAY)), ErrorCode.LOAN_LIMIT_EXCEEDED);
			verify(loanMapper).insert(any()); // 1 回目の分だけ
		}

		@ParameterizedTest(name = "廃棄済み={0}・貸出中={1}・延滞={2}・上限={3} → {4}")
		@CsvSource({ "true,  true,  true,  true,  EQUIPMENT_DISPOSED", "false, true,  true,  true,  EQUIPMENT_ALREADY_LENT",
				"false, false, true,  true,  BORROWER_HAS_OVERDUE", "false, false, false, true,  LOAN_LIMIT_EXCEEDED" })
		@DisplayName("判定の順番: 複数に当たる時は 廃棄済み → 貸出中 → 延滞 → 件数の上限 の順で最初のエラーを返す")
		void lend_reportsFirstViolationInDocumentedOrder(boolean disposed, boolean lent, boolean overdue,
				boolean atLimit, ErrorCode expected) {
			// どの判定まで進むかはケースで変わるので、使われない設定があっても失敗にしない（lenient）
			lenient().when(userMapper.lockById(YAMADA.id())).thenReturn(Optional.of(yamadaRow()));
			lenient().when(equipmentMapper.lockById(EQUIPMENT_ID))
				.thenReturn(Optional.of(equipment(disposed ? EquipmentStatus.DISPOSED : EquipmentStatus.ACTIVE)));
			lenient().when(equipmentMapper.hasOpenLoan(EQUIPMENT_ID)).thenReturn(lent);
			lenient().when(loanMapper.statsByBorrower(YAMADA.id(), TODAY))
				.thenReturn(new BorrowerLoanStats(atLimit ? 3 : 0, overdue ? 1 : 0));

			assertCode(() -> service.lend(YAMADA, request(TODAY)), expected);
			verify(loanMapper, never()).insert(any());
		}

		@Test
		@DisplayName("Clock を読むのは 1 回だけ: 23:59:59（日本時間）に期限＝今日で借りると、日付が変わる瞬間でも成功し、貸出日時も今日")
		void lend_readsClockOnce() {
			// 読むたびに 1 秒進む Clock。2 回読む実装なら、2 回目は翌日 0:00 になって判定と保存の日付が食い違う
			TickingClock clock = new TickingClock(Instant.parse("2026-10-05T14:59:59Z"));
			lendable(0, TODAY);
			insertAssignsIdAndViewReturns(TODAY);

			service(clock).lend(YAMADA, request(TODAY));

			ArgumentCaptor<NewLoan> inserted = ArgumentCaptor.forClass(NewLoan.class);
			verify(loanMapper).insert(inserted.capture());
			assertThat(inserted.getValue().getLentAt()).isEqualTo(OffsetDateTime.parse("2026-10-05T23:59:59+09:00"));
			assertThat(clock.reads()).isEqualTo(1);
		}

		@Test
		@DisplayName("判定をすり抜けて部分一意インデックス（uq_loans_open_equipment）に当たっても 409 EQUIPMENT_ALREADY_LENT")
		void lend_uniqueIndexViolation_isAlreadyLent() {
			lendable(0, TODAY);
			willThrow(duplicateKey(DbConstraints.UQ_LOANS_OPEN_EQUIPMENT)).given(loanMapper).insert(any());

			assertCode(() -> service.lend(YAMADA, request(TODAY)), ErrorCode.EQUIPMENT_ALREADY_LENT);
		}

		@Test
		@DisplayName("知らない制約の一意制約違反は変換しない（500 にして原因を取り違えない）")
		void lend_otherConstraint_isNotConverted() {
			lendable(0, TODAY);
			DuplicateKeyException other = duplicateKey("uq_something_else");
			willThrow(other).given(loanMapper).insert(any());

			assertThatThrownBy(() -> service.lend(YAMADA, request(TODAY))).isSameAs(other);
		}

	}

	@Nested
	class Return {

		@ParameterizedTest(name = "{0}")
		@CsvSource({ "借りた本人, 2, MEMBER", "ADMIN, 1, ADMIN" })
		@DisplayName("AC-12 借りた本人か ADMIN が返却できる。返却日時は Clock の時刻")
		void return_byBorrowerOrAdmin_succeeds(String who, long userId, Role role) {
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.of(openView(YAMADA.id(), TODAY)),
					Optional.of(returnedView(TODAY, NOW_TOKYO)));
			given(loanMapper.markReturned(LOAN_ID, NOW_TOKYO)).willReturn(1);

			LoanResponse response = service.returnLoan(new CurrentUser(userId, role), LOAN_ID);

			assertThat(response.state()).isEqualTo(LoanState.RETURNED);
			assertThat(response.returnedAt()).isEqualTo(NOW_TOKYO);
			assertThat(response.returnedLate()).isFalse();
		}

		@Test
		@DisplayName("AC-12 他の MEMBER は 403 で、返却の UPDATE を呼ばない")
		void return_otherMember_forbidden() {
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.of(openView(YAMADA.id(), TODAY)));

			assertCode(() -> service.returnLoan(SATO, LOAN_ID), ErrorCode.FORBIDDEN);
			verify(loanMapper, never()).markReturned(anyLong(), any());
		}

		@Test
		@DisplayName("無い貸出は 404 LOAN_NOT_FOUND")
		void return_notFound() {
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.empty());

			assertCode(() -> service.returnLoan(YAMADA, LOAN_ID), ErrorCode.LOAN_NOT_FOUND);
		}

		@Test
		@DisplayName("AC-12 条件付き UPDATE が 0 件なら返却済み（409 LOAN_ALREADY_RETURNED）")
		void return_alreadyReturned_conflict() {
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.of(returnedView(TODAY, NOW_TOKYO.minusDays(1))));
			given(loanMapper.markReturned(LOAN_ID, NOW_TOKYO)).willReturn(0);

			assertCode(() -> service.returnLoan(YAMADA, LOAN_ID), ErrorCode.LOAN_ALREADY_RETURNED);
		}

		@ParameterizedTest(name = "{0}（UTC）に返却 → returnedLate={1}")
		@CsvSource({ "2026-10-12T14:59:00Z, false", "2026-10-12T15:00:00Z, true" })
		@DisplayName("AC-12 returnedLate は日本時間の日付で判定する（期限当日 23:59 は false、翌日 0:00 は true。Clock も DB の値も UTC）")
		void return_lateFlag_usesBusinessZone(Instant now, boolean late) {
			LocalDate dueDate = LocalDate.of(2026, 10, 12);
			OffsetDateTime returnedAtUtc = now.atOffset(ZoneOffset.UTC); // DB から UTC のオフセットで読めた場合
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.of(openView(YAMADA.id(), dueDate)),
					Optional.of(returnedView(dueDate, returnedAtUtc)));
			given(loanMapper.markReturned(LOAN_ID, LENDING.timeOf(now))).willReturn(1);

			LoanResponse response = service(Clock.fixed(now, ZoneOffset.UTC)).returnLoan(YAMADA, LOAN_ID);

			assertThat(response.returnedLate()).isEqualTo(late);
			assertThat(response.returnedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
		}

	}

	@Nested
	class Read {

		@Test
		@DisplayName("AC-21 MEMBER が borrowerId を省くと自分に固定される（自分の id を指定しても同じ）")
		void list_memberSeesOwnOnly() {
			given(loanMapper.search(any())).willReturn(List.of());

			service.list(YAMADA, condition(null), new PageQuery(0, 20));
			service.list(YAMADA, condition(YAMADA.id()), new PageQuery(0, 20));

			ArgumentCaptor<LoanSearchQuery> queries = ArgumentCaptor.forClass(LoanSearchQuery.class);
			verify(loanMapper, times(2)).search(queries.capture());
			assertThat(queries.getAllValues()).extracting(LoanSearchQuery::borrowerId)
				.containsExactly(YAMADA.id(), YAMADA.id());
		}

		@Test
		@DisplayName("AC-21 MEMBER が他人の borrowerId を指定すると 403（SQL は実行しない）")
		void list_memberOtherBorrower_forbidden() {
			assertCode(() -> service.list(YAMADA, condition(SATO.id()), new PageQuery(0, 20)), ErrorCode.FORBIDDEN);
			verifyNoInteractions(loanMapper);
		}

		@Test
		@DisplayName("AC-21 ADMIN は全員分（borrowerId を省けば絞らない、指定すればその人）")
		void list_adminSeesAll() {
			given(loanMapper.search(any())).willReturn(List.of());

			service.list(ADMIN, condition(null), new PageQuery(0, 20));
			service.list(ADMIN, condition(SATO.id()), new PageQuery(0, 20));

			ArgumentCaptor<LoanSearchQuery> queries = ArgumentCaptor.forClass(LoanSearchQuery.class);
			verify(loanMapper, times(2)).search(queries.capture());
			assertThat(queries.getAllValues()).extracting(LoanSearchQuery::borrowerId).containsExactly(null, SATO.id());
		}

		@Test
		@DisplayName("一覧: 条件・並び・ページの位置を渡し、各行の state は今日の日付で求める")
		void list_buildsQueryAndDerivesState() {
			given(loanMapper.count(any())).willReturn(41L);
			given(loanMapper.search(any())).willReturn(List.of(openView(YAMADA.id(), TODAY.minusDays(1)),
					openView(YAMADA.id(), TODAY), returnedView(TODAY, NOW_TOKYO)));
			LoanSearchCondition condition = new LoanSearchCondition(LoanStateFilter.ALL, null, EQUIPMENT_ID,
					SortOrder.asc(LoanSortKey.DUE_DATE));

			PageResult<LoanResponse> result = service.list(ADMIN, condition, new PageQuery(2, 20));

			LoanSearchQuery expected = new LoanSearchQuery(LoanStateFilter.ALL, null, EQUIPMENT_ID,
					SortOrder.asc(LoanSortKey.DUE_DATE), 20, 40);
			verify(loanMapper).search(expected);
			verify(loanMapper).count(expected);
			assertThat(result.items()).extracting(LoanResponse::state)
				.containsExactly(LoanState.OVERDUE, LoanState.LENT, LoanState.RETURNED);
			assertThat(result.totalElements()).isEqualTo(41);
			assertThat(result.totalPages()).isEqualTo(3);
		}

		@Test
		@DisplayName("AC-22 他人の貸出の詳細は MEMBER には 403。借りた本人と ADMIN は見られる")
		void get_otherMembersLoan_forbidden() {
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.of(openView(YAMADA.id(), TODAY)));

			assertCode(() -> service.get(SATO, LOAN_ID), ErrorCode.FORBIDDEN);
			assertThat(service.get(YAMADA, LOAN_ID).borrower().displayName()).isEqualTo("山田 太郎");
			assertThat(service.get(ADMIN, LOAN_ID).equipment().assetCode()).isEqualTo("PC-0007");
		}

		@Test
		@DisplayName("無い貸出の詳細は 404 LOAN_NOT_FOUND")
		void get_notFound() {
			given(loanMapper.findView(LOAN_ID)).willReturn(Optional.empty());

			assertCode(() -> service.get(ADMIN, LOAN_ID), ErrorCode.LOAN_NOT_FOUND);
		}

		@Test
		@DisplayName("AC-03 MEMBER は延滞一覧を見られない（403・SQL は実行しない）")
		void memberCannotListOverdue() {
			assertCode(() -> service.listOverdue(YAMADA, new PageQuery(0, 20)), ErrorCode.FORBIDDEN);
			verifyNoInteractions(loanMapper);
		}

		@Test
		@DisplayName("延滞一覧（ADMIN）: 今日の日付とページの位置を渡し、延滞日数とメールアドレスを返す")
		void listOverdue_admin() {
			given(loanMapper.countOverdue(TODAY)).willReturn(1L);
			given(loanMapper.findOverdue(TODAY, 20, 0)).willReturn(List.of(new OverdueLoanView(LOAN_ID, EQUIPMENT_ID,
					"PC-0007", "ノート PC 14 インチ", YAMADA.id(), "山田 太郎", "yamada@example.com",
					OffsetDateTime.parse("2026-09-28T01:00:00Z"), TODAY.minusDays(2), 2)));

			PageResult<OverdueLoanResponse> result = service.listOverdue(ADMIN, new PageQuery(0, 20));

			assertThat(result.items()).singleElement().satisfies(item -> {
				assertThat(item.overdueDays()).isEqualTo(2);
				assertThat(item.borrower().email()).isEqualTo("yamada@example.com");
				assertThat(item.lentAt()).isEqualTo(OffsetDateTime.parse("2026-09-28T10:00:00+09:00"));
			});
			assertThat(result.totalElements()).isEqualTo(1);
		}

		private static LoanSearchCondition condition(Long borrowerId) {
			return new LoanSearchCondition(LoanStateFilter.OPEN, borrowerId, null, SortOrder.desc(LoanSortKey.LENT_AT));
		}

	}

	private static UserRow yamadaRow() {
		return new UserRow(YAMADA.id(), "山田 太郎", "yamada@example.com", Role.MEMBER);
	}

	private static EquipmentRow equipment(EquipmentStatus status) {
		OffsetDateTime createdAt = NOW_TOKYO.minusDays(30);
		return new EquipmentRow(EQUIPMENT_ID, "PC-0007", "ノート PC 14 インチ", EquipmentCategory.LAPTOP, null, status,
				status == EquipmentStatus.DISPOSED ? createdAt.plusDays(1) : null, 0, createdAt);
	}

	private static LoanView openView(long borrowerId, LocalDate dueDate) {
		return new LoanView(LOAN_ID, EQUIPMENT_ID, "PC-0007", "ノート PC 14 インチ", borrowerId, "山田 太郎",
				NOW_TOKYO.minusDays(1), dueDate, null);
	}

	private static LoanView returnedView(LocalDate dueDate, OffsetDateTime returnedAt) {
		return new LoanView(LOAN_ID, EQUIPMENT_ID, "PC-0007", "ノート PC 14 インチ", YAMADA.id(), "山田 太郎",
				NOW_TOKYO.minusDays(3), dueDate, returnedAt);
	}

	private static void assertCode(Executable call, ErrorCode expected) {
		BusinessException ex = catchThrowableOfType(BusinessException.class, call::execute);
		assertThat(ex).as("BusinessException が投げられること").isNotNull();
		assertThat(ex.code()).isEqualTo(expected);
	}

	/** 読むたびに 1 秒進む Clock（時刻を何回読んだかも数える）。 */
	private static final class TickingClock extends Clock {

		private Instant next;

		private int reads;

		TickingClock(Instant start) {
			this.next = start;
		}

		int reads() {
			return reads;
		}

		@Override
		public Instant instant() {
			reads++;
			Instant current = next;
			next = next.plusSeconds(1);
			return current;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

	}

}
