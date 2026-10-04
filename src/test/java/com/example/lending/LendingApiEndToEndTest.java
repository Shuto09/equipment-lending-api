package com.example.lending;

import java.net.URI;
import java.time.OffsetDateTime;

import com.example.lending.support.AppDbTest;
import com.example.lending.support.MutableClock;
import com.example.lending.support.TestData;
import com.example.lending.user.AuthenticationInterceptor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E（docs/テスト計画.md §4-4）。本物の PostgreSQL につないだアプリを HTTP で叩き、登録から廃棄までを 1 本通す。
 * 手順の番号はテスト計画の番号と同じ。時計は 2026-10-05 10:00 日本時間から始まり、手順 6 で 2026-10-14 8:00 日本時間
 * （UTC ではまだ 10-13）に進める。業務日付を UTC で求める誤りがあると、手順 6 の延滞日数が 1 になって赤になる。
 */
@AppDbTest
class LendingApiEndToEndTest {

	private static final String PROBLEM_TYPE_BASE = "https://example.com/problems/";

	@Autowired
	private RestTestClient client;

	@Autowired
	private TestData data;

	@Autowired
	private MutableClock clock;

	private long admin;

	private long yamada;

	private long sato;

	@BeforeEach
	void reset() {
		data.reset();
		admin = data.admin();
		yamada = data.yamada();
		sato = data.sato();
	}

	@Test
	@DisplayName("E2E AC-01・AC-06・AC-07・AC-08・AC-12・AC-13・AC-14・AC-19・AC-24 登録 → 検索 → 貸出 → 二重貸出の 409 → 延滞一覧 → 返却 → 廃棄 → 401")
	void registerLendReturnDispose() {
		// 1. ADMIN が PC-0001 を登録 → 201・Location を GET すると同じ内容
		EntityExchangeResult<String> registered = post("/api/equipment", admin, """
				{"assetCode": "PC-0001", "name": "ノート PC 14 インチ", "category": "LAPTOP"}""")
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult();
		String created = registered.getResponseBody();
		long equipmentId = JsonPath.<Number>read(created, "$.id").longValue();
		assertThat(JsonPath.<String>read(created, "$.availability")).isEqualTo("AVAILABLE");
		URI location = registered.getResponseHeaders().getLocation();
		assertThat(location).isNotNull();
		assertThat(location.getPath()).isEqualTo("/api/equipment/" + equipmentId);
		get(location, admin).expectStatus().isOk().expectBody().json(created, JsonCompareMode.STRICT);

		// 2. 山田が空いている備品を探す → 含まれる
		get("/api/equipment?availability=AVAILABLE", yamada).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.totalElements")
			.isEqualTo(1)
			.jsonPath("$.items[0].assetCode")
			.isEqualTo("PC-0001");

		// 3. 山田が期限 2026-10-12 で借りる → 201
		String loan = post("/api/loans", yamada, """
				{"equipmentId": %d, "dueDate": "2026-10-12"}""".formatted(equipmentId))
			.expectStatus()
			.isCreated()
			.expectHeader()
			.exists("Location")
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		long loanId = JsonPath.<Number>read(loan, "$.id").longValue();
		assertThat(JsonPath.<String>read(loan, "$.state")).isEqualTo("LENT");
		assertThat(JsonPath.<String>read(loan, "$.lentAt")).isEqualTo("2026-10-05T10:00:00+09:00");

		// 4. 佐藤が探す → 借り手「山田 太郎」と返却期限が出る。メールアドレスは出ない
		String searchedBySato = get("/api/equipment", sato).expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(JsonPath.<String>read(searchedBySato, "$.items[0].availability")).isEqualTo("LENT");
		assertThat(JsonPath.<String>read(searchedBySato, "$.items[0].currentLoan.borrower.displayName"))
			.isEqualTo("山田 太郎");
		assertThat(JsonPath.<String>read(searchedBySato, "$.items[0].currentLoan.dueDate")).isEqualTo("2026-10-12");
		assertThat(searchedBySato).doesNotContain("email").doesNotContain("@example.com");

		// 5. 佐藤が同じ備品を借りる → 409 EQUIPMENT_ALREADY_LENT（type はそのコードの URI）
		post("/api/loans", sato, """
				{"equipmentId": %d, "dueDate": "2026-10-12"}""".formatted(equipmentId))
			.expectStatus()
			.isEqualTo(409)
			.expectHeader()
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("EQUIPMENT_ALREADY_LENT")
			.jsonPath("$.type")
			.isEqualTo(PROBLEM_TYPE_BASE + "equipment-already-lent")
			.jsonPath("$.instance")
			.isEqualTo("/api/loans");

		// 6. 時計を 2026-10-14 8:00 日本時間（UTC では 10-13 23:00）に進める → 延滞一覧に 1 件・延滞日数 2・メールアドレスを含む
		clock.set(OffsetDateTime.parse("2026-10-14T08:00:00+09:00").toInstant());
		get("/api/loans/overdue", admin).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.totalElements")
			.isEqualTo(1)
			.jsonPath("$.items[0].id")
			.isEqualTo(loanId)
			.jsonPath("$.items[0].overdueDays")
			.isEqualTo(2)
			.jsonPath("$.items[0].borrower.email")
			.isEqualTo("yamada@example.com");

		// 7. 山田が返却 → 200・期限の後なので returnedLate=true
		post("/api/loans/" + loanId + "/return", yamada, null).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.state")
			.isEqualTo("RETURNED")
			.jsonPath("$.returnedAt")
			.isEqualTo("2026-10-14T08:00:00+09:00")
			.jsonPath("$.returnedLate")
			.isEqualTo(true);

		// 8. ADMIN が廃棄 → 200。佐藤が借りる → 409 EQUIPMENT_DISPOSED。既定の検索結果から消える
		post("/api/equipment/" + equipmentId + "/dispose", admin, null).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.availability")
			.isEqualTo("DISPOSED");
		post("/api/loans", sato, """
				{"equipmentId": %d, "dueDate": "2026-10-20"}""".formatted(equipmentId))
			.expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("EQUIPMENT_DISPOSED");
		get("/api/equipment", sato).expectStatus().isOk().expectBody().jsonPath("$.totalElements").isEqualTo(0);

		// 9. X-User-Id なしで検索 → 401
		client.get()
			.uri("/api/equipment")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("UNAUTHENTICATED");
	}

	private ResponseSpec get(String uri, long userId) {
		return client.get().uri(uri).header(AuthenticationInterceptor.HEADER, String.valueOf(userId)).exchange();
	}

	private ResponseSpec get(URI uri, long userId) {
		return client.get().uri(uri).header(AuthenticationInterceptor.HEADER, String.valueOf(userId)).exchange();
	}

	private ResponseSpec post(String uri, long userId, String json) {
		RestTestClient.RequestBodySpec request = client.post()
			.uri(uri)
			.header(AuthenticationInterceptor.HEADER, String.valueOf(userId));
		if (json == null) {
			return request.exchange();
		}
		return request.contentType(MediaType.APPLICATION_JSON).body(json).exchange();
	}

}
