package com.example.lending.equipment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.example.lending.equipment.EquipmentResponse.CurrentLoan;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 備品の API（Web 層）。サービスと、認証の interceptor が使う UserMapper は差し替える。 */
@WebMvcTest(EquipmentController.class)
class EquipmentControllerTest {

	private static final CurrentUser ADMIN = new CurrentUser(1, Role.ADMIN);

	private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-01T09:00:00+09:00");

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private EquipmentService equipmentService;

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
	@DisplayName("AC-01 登録は 201 と Location。本文は登録した備品（AVAILABLE）")
	void register_returns201WithLocation() {
		given(equipmentService.register(any(), any())).willReturn(response(Availability.AVAILABLE, null));

		MvcTestResult result = mvc.post()
			.uri("/api/equipment")
			.header("X-User-Id", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"assetCode": "PC-0001", "name": "ノート PC 14 インチ", "category": "LAPTOP"}""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "http://localhost/api/equipment/10");
		assertThat(result).bodyJson().extractingPath("$.availability").isEqualTo("AVAILABLE");
		verify(equipmentService).register(ADMIN,
				new RegisterEquipmentRequest("PC-0001", "ノート PC 14 インチ", EquipmentCategory.LAPTOP, null));
	}

	@Test
	@DisplayName("検索の引数がサービスに渡る（区分の複数指定・並び替え・ページ）")
	void search_passesConditionToService() {
		given(equipmentService.search(any(), any())).willReturn(new PageResult<>(List.of(), 1, 5, 0, 0));

		assertThat(mvc.get()
			.uri("/api/equipment?keyword=pc&category=LAPTOP&category=MONITOR&availability=LENT&sort=name,desc&page=1&size=5")
			.header("X-User-Id", "2")).hasStatusOk();

		verify(equipmentService).search(new EquipmentSearchCondition("pc",
				List.of(EquipmentCategory.LAPTOP, EquipmentCategory.MONITOR), Availability.LENT,
				SortOrder.desc(EquipmentSortKey.NAME)), new PageQuery(1, 5));
	}

	@Test
	@DisplayName("区分の空の指定・重複は除いてから渡す（500 にしない）")
	void search_ignoresEmptyCategory() {
		given(equipmentService.search(any(), any())).willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/equipment?category=LAPTOP&category=&category=LAPTOP").header("X-User-Id", "2"))
			.hasStatusOk();

		verify(equipmentService).search(new EquipmentSearchCondition(null, List.of(EquipmentCategory.LAPTOP), null,
				SortOrder.asc(EquipmentSortKey.ASSET_CODE)), new PageQuery(0, 20));
	}

	@Test
	@DisplayName("検索の既定: 区分なし・管理番号の昇順・0 ページ目・20 件")
	void search_defaults() {
		given(equipmentService.search(any(), any())).willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/equipment").header("X-User-Id", "2")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}""");

		verify(equipmentService).search(
				new EquipmentSearchCondition(null, List.of(), null, SortOrder.asc(EquipmentSortKey.ASSET_CODE)),
				new PageQuery(0, 20));
	}

	@Test
	@DisplayName("AC-24 検索・詳細に借り手の表示名と返却期限が出る。メールアドレスは出ない")
	void search_showsBorrowerNameWithoutEmail() {
		CurrentLoan loan = new CurrentLoan(new UserSummary(3, "山田 太郎"), LocalDate.of(2026, 10, 12));
		given(equipmentService.search(any(), any())).willReturn(new PageResult<>(
				List.of(new EquipmentSummaryResponse(10, "PC-0001", "ノート PC", EquipmentCategory.LAPTOP,
						Availability.LENT, loan, CREATED_AT)),
				0, 20, 1, 1));
		given(equipmentService.get(10)).willReturn(response(Availability.LENT, loan));

		MvcTestResult search = mvc.get().uri("/api/equipment").header("X-User-Id", "2").exchange();
		MvcTestResult detail = mvc.get().uri("/api/equipment/10").header("X-User-Id", "2").exchange();

		assertThat(search).bodyJson().isLenientlyEqualTo("""
				{"items": [{"assetCode": "PC-0001", "availability": "LENT",
				            "currentLoan": {"borrower": {"id": 3, "displayName": "山田 太郎"}, "dueDate": "2026-10-12"}}]}""");
		assertThat(detail).bodyJson().isLenientlyEqualTo("""
				{"availability": "LENT", "currentLoan": {"borrower": {"displayName": "山田 太郎"}, "dueDate": "2026-10-12"}}""");
		for (MvcTestResult result : List.of(search, detail)) {
			assertThat(result).hasStatusOk().bodyText().doesNotContain("email").doesNotContain("@example.com");
		}
	}

	@Test
	@DisplayName("詳細は 200。日時はオフセット付き")
	void get_returns200() {
		given(equipmentService.get(10)).willReturn(response(Availability.AVAILABLE, null));

		assertThat(mvc.get().uri("/api/equipment/10").header("X-User-Id", "2")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 10, "assetCode": "PC-0001", "category": "LAPTOP", "version": 0,
					 "createdAt": "2026-10-01T09:00:00+09:00", "currentLoan": null}""");
	}

	@Test
	@DisplayName("PATCH は 200。送った項目と version がサービスに渡る")
	void patch_returns200() {
		given(equipmentService.update(any(), anyLong(), any())).willReturn(response(Availability.AVAILABLE, null));

		assertThat(mvc.patch()
			.uri("/api/equipment/10")
			.header("X-User-Id", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"remarks": "", "version": 3}""")).hasStatusOk();

		verify(equipmentService).update(ADMIN, 10, new UpdateEquipmentRequest(null, null, "", 3));
	}

	@Test
	@DisplayName("廃棄は 200")
	void dispose_returns200() {
		given(equipmentService.dispose(ADMIN, 10)).willReturn(response(Availability.DISPOSED, null));

		assertThat(mvc.post().uri("/api/equipment/10/dispose").header("X-User-Id", "1")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.availability")
			.isEqualTo("DISPOSED");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			'{}'                                                                          | #/assetCode
			'{"assetCode": "PC-0001", "category": "LAPTOP"}'                              | #/name
			'{"assetCode": "pc-1", "name": "ノート PC", "category": "LAPTOP"}'              | #/assetCode
			'{"assetCode": "PC-00001", "name": "ノート PC", "category": "LAPTOP"}'          | #/assetCode
			'{"assetCode": "PC-0001", "name": "   ", "category": "LAPTOP"}'               | #/name
			'{"assetCode": "PC-0001", "name": "ノート PC"}'                                 | #/category
			""")
	@DisplayName("登録の入力検証は 400。errors の pointer に項目名")
	void register_invalidBody_returns400(String body, String pointer) {
		assertThat(mvc.post()
			.uri("/api/equipment")
			.header("X-User-Id", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].pointer")
			.asArray()
			.contains(pointer);
		verifyNoInteractions(equipmentService);
	}

	@Test
	@DisplayName("名称が 100 字を超えると 400（100 字は通る）")
	void register_nameTooLong_returns400() {
		given(equipmentService.register(any(), any())).willReturn(response(Availability.AVAILABLE, null));
		String json = """
				{"assetCode": "PC-0001", "name": "%s", "category": "LAPTOP"}""";

		assertThat(mvc.post()
			.uri("/api/equipment")
			.header("X-User-Id", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.formatted("あ".repeat(101)))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].pointer")
			.asArray()
			.containsExactly("#/name");
		assertThat(mvc.post()
			.uri("/api/equipment")
			.header("X-User-Id", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.formatted("あ".repeat(100)))).hasStatus(HttpStatus.CREATED);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			'{"version": 1}'                    | 変更する項目（name・category・remarks）を 1 つ以上送ってください
			'{"name": "   ", "version": 1}'     | 空白だけの名称は使えません
			'{"name": "新しい名前"}'              | null は許可されていません
			""")
	@DisplayName("AC-23 変更する項目が 0 個・空白だけの名称・version なしの PATCH は 400")
	void patch_noFieldsOrBlankName_returns400(String body, String detail) {
		assertThat(mvc.patch()
			.uri("/api/equipment/10")
			.header("X-User-Id", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].detail")
			.asArray()
			.contains(detail);
		verifyNoInteractions(equipmentService);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
			size=101       | size
			size=0         | size
			page=-1        | page
			page=10001     | page
			page=abc       | page
			category=FOO   | category
			availability=x | availability
			sort=price     | sort
			""")
	@DisplayName("検索の入力検証は 400。errors の parameter に項目名（SQL は実行しない）")
	void search_invalidQuery_returns400(String query, String parameter) {
		assertThat(mvc.get().uri("/api/equipment?" + query).header("X-User-Id", "2"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].parameter")
			.asArray()
			.containsExactly(parameter);
		verifyNoInteractions(equipmentService);
	}

	@Test
	@DisplayName("キーワードが 50 字を超えると 400")
	void search_keywordTooLong_returns400() {
		assertThat(mvc.get().uri("/api/equipment?keyword=" + "a".repeat(51)).header("X-User-Id", "2"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].parameter")
			.asArray()
			.containsExactly("keyword");
	}

	@Test
	@DisplayName("AC-05 許可していない並び替えキーは 400 VALIDATION_FAILED（サービスを呼ばない）")
	void search_unknownSort_returns400() {
		assertThat(mvc.get().uri("/api/equipment?sort=name;DROP TABLE equipment").header("X-User-Id", "2"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"code": "VALIDATION_FAILED", "errors": [{"parameter": "sort"}]}""");
		verifyNoInteractions(equipmentService);
	}

	@Test
	@DisplayName("page の上限 10000 ちょうどは通る")
	void search_pageUpperBound_isAccepted() {
		given(equipmentService.search(any(), any())).willReturn(new PageResult<>(List.of(), 10000, 20, 0, 0));

		assertThat(mvc.get().uri("/api/equipment?page=10000").header("X-User-Id", "2")).hasStatusOk();
	}

	private static EquipmentResponse response(Availability availability, CurrentLoan loan) {
		return new EquipmentResponse(10, "PC-0001", "ノート PC 14 インチ", EquipmentCategory.LAPTOP, null, availability,
				loan, 0, CREATED_AT, null);
	}

}
