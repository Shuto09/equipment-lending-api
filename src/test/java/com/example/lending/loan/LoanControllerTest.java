package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.example.lending.loan.OverdueLoanResponse.BorrowerContact;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.PageResult;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.user.CurrentUser;
import com.example.lending.user.Role;
import com.example.lending.user.UserMapper;
import com.example.lending.user.UserRow;
import com.example.lending.user.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 貸出の API（Web 層）。サービスと、認証の interceptor が使う UserMapper は差し替える。 */
@WebMvcTest(LoanController.class)
class LoanControllerTest {

	private static final CurrentUser ADMIN = new CurrentUser(1, Role.ADMIN);

	private static final CurrentUser YAMADA = new CurrentUser(2, Role.MEMBER);

	private static final LocalDate DUE_DATE = LocalDate.of(2026, 10, 12);

	private static final OffsetDateTime LENT_AT = OffsetDateTime.parse("2026-10-05T10:15:00+09:00");

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private LoanService loanService;

	@MockitoBean
	private UserMapper userMapper;

	@BeforeEach
	void users() {
		given(userMapper.findById(1L))
			.willReturn(Optional.of(new UserRow(1, "鈴木 一郎", "suzuki@example.com", Role.ADMIN)));
		given(userMapper.findById(2L))
			.willReturn(Optional.of(new UserRow(2, "山田 太郎", "yamada@example.com", Role.MEMBER)));
	}

	@Test
	@DisplayName("AC-06 貸出は 201 と Location。本文は設計 §6 の例と同じ形")
	void lend_returns201WithLocation() {
		given(loanService.lend(any(), any())).willReturn(lent());

		MvcTestResult result = mvc.post()
			.uri("/api/loans")
			.header("X-User-Id", "2")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"equipmentId": 7, "dueDate": "2026-10-12"}""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "http://localhost/api/loans/42");
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "id": 42,
				  "equipment": { "id": 7, "assetCode": "PC-0007", "name": "ノート PC 14 インチ" },
				  "borrower": { "id": 2, "displayName": "山田 太郎" },
				  "lentAt": "2026-10-05T10:15:00+09:00",
				  "dueDate": "2026-10-12",
				  "returnedAt": null,
				  "state": "LENT",
				  "returnedLate": false
				}""");
		verify(loanService).lend(YAMADA, new LendRequest(7L, DUE_DATE));
	}

	@Test
	@DisplayName("AC-07 業務エラーは ProblemDetail（409・code・コードごとの type）")
	void lend_alreadyLent_returnsProblem() {
		given(loanService.lend(any(), any()))
			.willThrow(new BusinessException(ErrorCode.EQUIPMENT_ALREADY_LENT, "備品 PC-0007 は貸出中です"));

		assertThat(mvc.post()
			.uri("/api/loans")
			.header("X-User-Id", "2")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"equipmentId": 7, "dueDate": "2026-10-12"}""")).hasStatus(HttpStatus.CONFLICT)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.isStrictlyEqualTo("""
					{
					  "type": "https://example.com/problems/equipment-already-lent",
					  "title": "貸出中の備品です",
					  "status": 409,
					  "detail": "備品 PC-0007 は貸出中です",
					  "instance": "/api/loans",
					  "code": "EQUIPMENT_ALREADY_LENT"
					}""");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			'{"equipmentId": 7, "dueDate": "2026/10/12"}'   | #/dueDate     | 日付は yyyy-MM-dd の形で指定してください
			'{"equipmentId": 7, "dueDate": "2026-02-30"}'   | #/dueDate     | 日付は yyyy-MM-dd の形で指定してください
			'{"equipmentId": 7}'                            | #/dueDate     | null は許可されていません
			'{"dueDate": "2026-10-12"}'                     | #/equipmentId | null は許可されていません
			'{"equipmentId": "PC-0007", "dueDate": "2026-10-12"}' | #/equipmentId | 整数で指定してください
			""")
	@DisplayName("貸出の入力検証は 400。errors の pointer に項目名（サービスを呼ばない）")
	void lend_invalidBody_returns400(String body, String pointer, String detail) {
		assertThat(mvc.post()
			.uri("/api/loans")
			.header("X-User-Id", "2")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"code": "VALIDATION_FAILED", "errors": [{"pointer": "%s", "detail": "%s"}]}""".formatted(pointer,
					detail));
		verifyNoInteractions(loanService);
	}

	@Test
	@DisplayName("壊れた JSON は 400（位置が分からないので errors は空）")
	void lend_brokenJson_returns400() {
		assertThat(mvc.post()
			.uri("/api/loans")
			.header("X-User-Id", "2")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"equipmentId\": 7, \"dueDate\": ")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors")
			.asArray()
			.isEmpty();
		verifyNoInteractions(loanService);
	}

	@Test
	@DisplayName("一覧の引数がサービスに渡る（状態・借り手・備品・並び替え・ページ）")
	void list_passesConditionToService() {
		given(loanService.list(any(), any(), any())).willReturn(new PageResult<>(List.of(lent()), 1, 5, 6, 2));

		assertThat(mvc.get()
			.uri("/api/loans?state=ALL&borrowerId=3&equipmentId=7&sort=dueDate,asc&page=1&size=5")
			.header("X-User-Id", "1")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"items": [{"id": 42, "state": "LENT"}], "page": 1, "size": 5, "totalElements": 6, "totalPages": 2}""");

		verify(loanService).list(ADMIN,
				new LoanSearchCondition(LoanStateFilter.ALL, 3L, 7L, SortOrder.asc(LoanSortKey.DUE_DATE)),
				new PageQuery(1, 5));
	}

	@Test
	@DisplayName("一覧の既定: 未返却（OPEN）・借り手と備品で絞らない・貸出日時の降順・0 ページ目・20 件")
	void list_defaults() {
		given(loanService.list(any(), any(), any())).willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/loans").header("X-User-Id", "2")).hasStatusOk();

		verify(loanService).list(YAMADA,
				new LoanSearchCondition(LoanStateFilter.OPEN, null, null, SortOrder.desc(LoanSortKey.LENT_AT)),
				new PageQuery(0, 20));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			/api/loans?state=LENT          | state
			/api/loans?borrowerId=abc      | borrowerId
			/api/loans?sort=name           | sort
			/api/loans?sort=lentAt,up      | sort
			/api/loans?size=101            | size
			/api/loans?page=-1             | page
			/api/loans/overdue?size=0      | size
			/api/loans/overdue?page=10001  | page
			/api/loans/abc                 | id
			""")
	@DisplayName("クエリ・パスの入力検証は 400。errors の parameter に項目名（サービスを呼ばない）")
	void invalidQuery_returns400(String uri, String parameter) {
		assertThat(mvc.get().uri(uri).header("X-User-Id", "1")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].parameter")
			.asArray()
			.containsExactly(parameter);
		verifyNoInteractions(loanService);
	}

	@Test
	@DisplayName("詳細は 200")
	void get_returns200() {
		given(loanService.get(YAMADA, 42)).willReturn(lent());

		assertThat(mvc.get().uri("/api/loans/42").header("X-User-Id", "2")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 42, "borrower": {"id": 2, "displayName": "山田 太郎"}, "state": "LENT"}""");
	}

	@Test
	@DisplayName("返却は 200。returnedAt と returnedLate が入る")
	void return_returns200() {
		LoanResponse returned = new LoanResponse(42, new EquipmentRef(7, "PC-0007", "ノート PC 14 インチ"),
				new UserSummary(2, "山田 太郎"), LENT_AT, DUE_DATE, OffsetDateTime.parse("2026-10-14T09:30:00+09:00"),
				LoanState.RETURNED, true);
		given(loanService.returnLoan(YAMADA, 42)).willReturn(returned);

		assertThat(mvc.post().uri("/api/loans/42/return").header("X-User-Id", "2")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 42, "returnedAt": "2026-10-14T09:30:00+09:00", "state": "RETURNED", "returnedLate": true}""");
	}

	@Test
	@DisplayName("延滞一覧は 200。延滞日数と借り手のメールアドレスが入る（/api/loans/{id} ではなく延滞一覧に当たる）")
	void overdue_returns200() {
		given(loanService.listOverdue(ADMIN, new PageQuery(0, 20))).willReturn(new PageResult<>(
				List.of(new OverdueLoanResponse(42, new EquipmentRef(7, "PC-0007", "ノート PC 14 インチ"),
						new BorrowerContact(2, "山田 太郎", "yamada@example.com"), LENT_AT, DUE_DATE, 2)),
				0, 20, 1, 1));

		assertThat(mvc.get().uri("/api/loans/overdue").header("X-User-Id", "1")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"items": [{"id": 42, "dueDate": "2026-10-12", "overdueDays": 2,
					            "borrower": {"id": 2, "displayName": "山田 太郎", "email": "yamada@example.com"}}],
					 "totalElements": 1}""");
	}

	@Test
	@DisplayName("AC-19 X-User-Id が無ければ、本文が不正でも 401（400 より先）")
	void unauthenticated_beforeValidation() {
		assertThat(mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("UNAUTHENTICATED");
		verifyNoInteractions(loanService);
	}

	private static LoanResponse lent() {
		return new LoanResponse(42, new EquipmentRef(7, "PC-0007", "ノート PC 14 インチ"), new UserSummary(2, "山田 太郎"),
				LENT_AT, DUE_DATE, null, LoanState.LENT, false);
	}

}
