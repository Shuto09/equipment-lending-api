package com.example.lending.shared.error;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.example.lending.equipment.Availability;
import com.example.lending.equipment.EquipmentCategory;
import com.example.lending.equipment.EquipmentController;
import com.example.lending.equipment.EquipmentResponse;
import com.example.lending.equipment.EquipmentService;
import com.example.lending.user.Role;
import com.example.lending.user.UserMapper;
import com.example.lending.user.UserRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * エラー応答の形（ProblemDetail）と認証の順番（docs/要件と設計.md §7・§12）。
 * 起こしにくい例外は、このテスト専用のコントローラ（/api/test/**）から投げる。
 */
@WebMvcTest(EquipmentController.class)
@Import(ApiExceptionHandlerTest.ThrowingController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

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
		given(userMapper.findById(99L)).willReturn(Optional.empty());
	}

	@ParameterizedTest(name = "X-User-Id={0}")
	@NullSource
	@ValueSource(strings = { "", "abc", "-1", "1.0", "99", "1234567890123456789" })
	@DisplayName("AC-19 X-User-Id が無い・数字でない・存在しない利用者は 401（検索・詳細でも）")
	void missingOrUnknownUser_returns401(String userId) {
		for (String uri : new String[] { "/api/equipment", "/api/equipment/10" }) {
			MockMvcRequestBuilder request = mvc.get().uri(uri);
			if (userId != null) {
				request.header("X-User-Id", userId);
			}
			assertProblem(request.exchange(), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
		}
		verifyNoInteractions(equipmentService);
	}

	@Test
	@DisplayName("AC-19 本文が不正な POST をヘッダなしで送っても 401（400 より先）")
	void unauthenticated_beforeValidation() {
		assertProblem(mvc.post().uri("/api/equipment").contentType(MediaType.APPLICATION_JSON).content("{").exchange(),
				HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
		assertProblem(mvc.post().uri("/api/equipment").contentType(MediaType.APPLICATION_JSON).content("{}").exchange(),
				HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
	}

	@Test
	@DisplayName("入力: @Valid @RequestBody の違反（MethodArgumentNotValidException）は pointer")
	void bodyValidation_hasPointers() {
		MvcTestResult result = withUser(mvc.post().uri("/api/equipment").contentType(MediaType.APPLICATION_JSON).content("{}"));

		assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].pointer")
			.asArray()
			.containsExactly("#/assetCode", "#/category", "#/name");
	}

	@Test
	@DisplayName("入力: @RequestParam の制約の違反（HandlerMethodValidationException）は parameter")
	void parameterValidation_hasParameters() {
		MvcTestResult result = withUser(mvc.get().uri("/api/equipment?size=101&page=-1"));

		assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
		assertThat(result).bodyJson()
			.isLenientlyEqualTo("""
					{"errors": [{"parameter": "page", "detail": "0 以上の値にしてください"},
					            {"parameter": "size", "detail": "100 以下の値にしてください"}]}""");
	}

	@Test
	@DisplayName("入力: 型変換の失敗（MethodArgumentTypeMismatchException）は parameter。区分は選べる値を示す")
	void typeMismatch_hasParameter() {
		MvcTestResult result = withUser(mvc.get().uri("/api/equipment?category=FOO"));

		assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
		assertThat(result).bodyJson()
			.isLenientlyEqualTo("""
					{"errors": [{"parameter": "category",
					             "detail": "次のどれかを指定してください: LAPTOP, MONITOR, PROJECTOR, OTHER"}]}""");
	}

	@Test
	@DisplayName("入力: 必須のクエリパラメータが無い（MissingServletRequestParameterException）は parameter")
	void missingParameter_hasParameter() {
		MvcTestResult result = withUser(mvc.get().uri("/api/test/required"));

		assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
		assertThat(result).bodyJson().extractingPath("$.errors[0].parameter").isEqualTo("value");
	}

	@Test
	@DisplayName("入力: 読めない値（HttpMessageNotReadableException）は Jackson の位置から pointer")
	void unreadableValue_hasPointer() {
		MvcTestResult result = withUser(mvc.post()
			.uri("/api/equipment")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"assetCode": "PC-0001", "name": "ノート PC", "category": "PHONE"}"""));

		assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
		assertThat(result).bodyJson()
			.isLenientlyEqualTo("""
					{"errors": [{"pointer": "#/category",
					             "detail": "次のどれかを指定してください: LAPTOP, MONITOR, PROJECTOR, OTHER"}]}""");
	}

	@Test
	@DisplayName("入力: 壊れた JSON は 400（位置が分からないので errors は空）")
	void brokenJson_returns400() {
		MvcTestResult result = withUser(
				mvc.post().uri("/api/equipment").contentType(MediaType.APPLICATION_JSON).content("{\"assetCode\": "));

		assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
		assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
	}

	@Test
	@DisplayName("/api/ の下の存在しないパス: ヘッダありで 404（type は about:blank）、ヘッダなしで 401")
	void unknownPath_returns404OrUnauthorized() {
		MvcTestResult notFound = withUser(mvc.get().uri("/api/nothing-here"));
		assertProblem(notFound, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
		assertThat(notFound).bodyJson().isLenientlyEqualTo("""
				{"type": "about:blank", "title": "Not Found", "instance": "/api/nothing-here"}""");

		assertProblem(mvc.get().uri("/api/nothing-here").exchange(), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
	}

	@Test
	@DisplayName("405 はハンドラを選ぶ段階で決まるので、ヘッダが無くても 405")
	void methodNotAllowed_returns405() {
		MvcTestResult result = mvc.delete().uri("/api/equipment/10").exchange();

		assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("about:blank");
	}

	@Test
	@DisplayName("406: JSON 以外の応答を求めた（エラー応答そのものは problem+json で返る）")
	void notAcceptable_returns406() {
		given(equipmentService.get(anyLong())).willReturn(new EquipmentResponse(10, "PC-0001", "ノート PC",
				EquipmentCategory.LAPTOP, null, Availability.AVAILABLE, null, 0, OffsetDateTime.now(), null));

		MvcTestResult result = withUser(mvc.get().uri("/api/equipment/10").accept(MediaType.APPLICATION_XML));

		assertProblem(result, HttpStatus.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE);
	}

	@Test
	@DisplayName("415: 本文が JSON でない")
	void unsupportedMediaType_returns415() {
		MvcTestResult result = withUser(mvc.post().uri("/api/equipment").contentType(MediaType.TEXT_PLAIN).content("x"));

		assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("about:blank");
	}

	@Test
	@DisplayName("表に無い Spring MVC の 4xx は REQUEST_REJECTED（状態は元のまま）")
	void otherClientError_isRequestRejected() {
		MvcTestResult result = withUser(mvc.get().uri("/api/test/too-large"));

		assertProblem(result, HttpStatus.CONTENT_TOO_LARGE, ErrorCode.REQUEST_REJECTED);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("about:blank");
	}

	@Test
	@DisplayName("Spring MVC の 5xx は INTERNAL_ERROR と traceId（状態は元のまま）")
	void springServerError_isInternalError() {
		MvcTestResult result = withUser(mvc.get().uri("/api/test/timeout"));

		assertProblem(result, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR);
		assertThat(result).bodyJson().hasPath("$.traceId");
	}

	@ParameterizedTest
	@EnumSource(ErrorCode.class)
	@DisplayName("ErrorCode のすべてが HTTP の状態・code・type・title に変換される")
	void everyErrorCode_isConverted(ErrorCode code) {
		MvcTestResult result = withUser(mvc.get().uri("/api/test/business/{code}", code));

		assertProblem(result, code.status(), code);
		String title = code.title() != null ? code.title() : code.status().getReasonPhrase();
		assertThat(result).bodyJson()
			.isLenientlyEqualTo("""
					{"type": "%s", "title": "%s", "status": %d, "detail": "テスト用の説明", "instance": "/api/test/business/%s"}"""
				.formatted(code.type(), title, code.status().value(), code));
		if (code.title() != null) {
			assertThat(code.type().toString()).startsWith("https://example.com/problems/");
		}
	}

	@Test
	@DisplayName("AC-20 想定外の例外は 500 と traceId。本文に例外のクラス名・SQL・スタックトレースを含めず、同じ traceId をログに出す")
	void unexpectedException_hidesDetailsWithTraceId(CapturedOutput output) {
		MvcTestResult result = withUser(mvc.get().uri("/api/test/unexpected"));

		assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
		assertThat(result).bodyText()
			.doesNotContain("IllegalStateException")
			.doesNotContain("SELECT")
			.doesNotContain("secret_table")
			.doesNotContain("at com.example");
		assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank().satisfies(id -> {
			assertThat(output).contains("traceId=" + id);
			assertThat(output).contains("SELECT * FROM secret_table"); // 詳細はログにだけ出す
		});
	}

	private MvcTestResult withUser(MockMvcRequestBuilder request) {
		return request.header("X-User-Id", "1").exchange();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, ErrorCode code) {
		assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code.name());
		assertThat(result).bodyJson().extractingPath("$.status").asNumber().isEqualTo(status.value());
	}

	/** 起こしにくい例外を投げるテスト専用のコントローラ。 */
	@RestController
	static class ThrowingController {

		@GetMapping("/api/test/required")
		String required(@RequestParam String value) {
			return value;
		}

		@GetMapping("/api/test/too-large")
		String tooLarge() {
			throw new MaxUploadSizeExceededException(1);
		}

		@GetMapping("/api/test/timeout")
		String timeout() {
			throw new AsyncRequestTimeoutException();
		}

		@GetMapping("/api/test/business/{code}")
		String business(@PathVariable ErrorCode code) {
			throw new BusinessException(code, "テスト用の説明");
		}

		@GetMapping("/api/test/unexpected")
		String unexpected() {
			throw new IllegalStateException("SELECT * FROM secret_table WHERE id = 1");
		}

	}

}
