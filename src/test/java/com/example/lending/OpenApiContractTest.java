package com.example.lending;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.lending.support.AppDbTest;
import com.example.lending.user.AuthenticationInterceptor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 一覧（docs/要件と設計.md §6 の表）と、springdoc が出す OpenAPI（/v3/api-docs）が一致することの機械検査
 * （docs/テスト計画.md §3）。設計書の表をこのテストが読んで比べるので、API を足した・消した時に、
 * 設計書か実装のどちらかを直し忘れると赤になる。
 */
@AppDbTest
class OpenApiContractTest {

	private static final Path DESIGN_DOC = Path.of("docs", "要件と設計.md");

	/** §6 の表の行（例 {@code | 1 | POST | `/api/equipment` | ADMIN | …}）から、メソッドとパスを取り出す。 */
	private static final Pattern API_ROW = Pattern
		.compile("^\\|\\s*\\d+\\s*\\|\\s*(GET|POST|PUT|PATCH|DELETE)\\s*\\|\\s*`(/api/[^`]+)`\\s*\\|");

	private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "options", "head", "patch",
			"trace");

	@Autowired
	private RestTestClient client;

	private String apiDocs;

	@BeforeEach
	void fetchApiDocs() {
		// /v3/api-docs は /api/** の外なので X-User-Id は要らない
		apiDocs = client.get()
			.uri("/v3/api-docs")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

	@Test
	@DisplayName("OpenAPI の「メソッド＋パス」の集合が、設計 §6 の API 一覧の表と一致する")
	void operations_matchDesignedApiList() throws IOException {
		Set<String> designed = designedOperations();

		assertThat(designed).as("設計 §6 の表から API を読み取れること").isNotEmpty();
		assertThat(operations().keySet()).containsExactlyInAnyOrderElementsOf(designed);
	}

	@Test
	@DisplayName("すべての操作に、必須のヘッダ X-User-Id が載っている")
	void everyOperation_requiresUserIdHeader() {
		operations().forEach((operation, definition) -> {
			List<Map<String, Object>> headers = parameters(definition).stream()
				.filter(parameter -> "header".equals(parameter.get("in")))
				.filter(parameter -> AuthenticationInterceptor.HEADER.equals(parameter.get("name")))
				.toList();
			assertThat(headers).as(operation).singleElement().satisfies(header -> assertThat(header.get("required"))
				.as(operation)
				.isEqualTo(Boolean.TRUE));
		});
	}

	@Test
	@DisplayName("認証済みの利用者（CurrentUser）は、引数としても型としても載っていない")
	void currentUser_isNotExposed() {
		operations().forEach((operation, definition) -> assertThat(parameters(definition))
			.as(operation)
			.extracting(parameter -> parameter.get("name"))
			.doesNotContain("user"));
		assertThat(apiDocs).doesNotContain("CurrentUser");
	}

	/** 設計書の §6 の節（「## 6.」から「## 7.」の前まで）にある表の行を "POST /api/equipment" の形で集める。 */
	private static Set<String> designedOperations() throws IOException {
		String doc = Files.readString(DESIGN_DOC, StandardCharsets.UTF_8);
		int start = doc.indexOf("\n## 6. ");
		int end = doc.indexOf("\n## 7. ");
		assertThat(start).as("設計書に §6 がある").isNotNegative();
		assertThat(end).as("設計書に §7 がある").isGreaterThan(start);
		return doc.substring(start, end)
			.lines()
			.map(API_ROW::matcher)
			.filter(Matcher::find)
			.map(row -> row.group(1) + " " + row.group(2))
			.collect(Collectors.toSet());
	}

	/** "GET /api/equipment" → 操作の定義。パスの直下にある操作以外の項目（parameters など）は除く。 */
	@SuppressWarnings("unchecked")
	private Map<String, Map<String, Object>> operations() {
		Map<String, Map<String, Object>> paths = JsonPath.read(apiDocs, "$.paths");
		Map<String, Map<String, Object>> operations = new TreeMap<>();
		paths.forEach((path, item) -> item.forEach((method, definition) -> {
			if (HTTP_METHODS.contains(method)) {
				operations.put(method.toUpperCase(Locale.ROOT) + " " + path, (Map<String, Object>) definition);
			}
		}));
		return operations;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> parameters(Map<String, Object> operation) {
		Object parameters = operation.get("parameters");
		return parameters == null ? List.of() : (List<Map<String, Object>>) parameters;
	}

}
