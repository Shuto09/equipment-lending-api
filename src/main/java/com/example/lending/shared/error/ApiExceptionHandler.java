package com.example.lending.shared.error;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * すべての失敗応答を ProblemDetail（RFC 9457）にする 1 か所（docs/要件と設計.md §7）。
 * <ul>
 * <li>業務エラー（{@link BusinessException}）は ErrorCode の状態・type・title をそのまま使う。</li>
 * <li>Spring MVC 自身が投げる例外は {@link #handleExceptionInternal} で code（入力系は errors も）を付ける。</li>
 * <li>想定外の例外は 500。応答には一般的な文と traceId だけを返し、スタックトレースは同じ traceId でログにだけ出す。</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	static final String VALIDATION_DETAIL = "入力に誤りがあります。errors を確かめてください";

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
		ProblemDetail problem = problem(ex.code(), ex.code().status(), ex.getMessage());
		if (!ex.errors().isEmpty()) {
			problem.setProperty("errors", ex.errors());
		}
		return ResponseEntity.status(ex.code().status()).body(problem);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		return ResponseEntity.internalServerError().body(internalError(ex, HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/** Spring MVC の例外（405・415・入力検証など）。ここで code と errors を付ける。 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = status.is5xxServerError() ? internalError(ex, status) : clientError(ex, status);
		return super.handleExceptionInternal(ex, problem, headers, status, request);
	}

	static ProblemDetail problem(ErrorCode code, HttpStatusCode status, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(code.type());
		if (code.title() != null) {
			problem.setTitle(code.title()); // null のままなら HTTP の状態の名前になる
		}
		problem.setProperty("code", code.name());
		return problem;
	}

	private static ProblemDetail internalError(Exception ex, HttpStatusCode status) {
		String traceId = UUID.randomUUID().toString();
		log.error("想定外の例外 traceId={}", traceId, ex);
		ProblemDetail problem = problem(ErrorCode.INTERNAL_ERROR, status,
				"想定外のエラーが起きました。traceId を添えて問い合わせてください");
		problem.setProperty("traceId", traceId);
		return problem;
	}

	private static ProblemDetail clientError(Exception ex, HttpStatusCode status) {
		return switch (ex) {
			case MethodArgumentNotValidException e -> invalid(VALIDATION_DETAIL, bodyErrors(e.getBindingResult()));
			case HandlerMethodValidationException e -> invalid(VALIDATION_DETAIL, methodValidationErrors(e));
			case MethodArgumentTypeMismatchException e -> invalid(VALIDATION_DETAIL,
					List.of(ProblemError.forParameter(e.getName(), typeMessage(elementType(e.getParameter())))));
			case TypeMismatchException e -> invalid(VALIDATION_DETAIL,
					List.of(ProblemError.forParameter(e.getPropertyName(), typeMessage(e.getRequiredType()))));
			case MissingServletRequestParameterException e -> invalid(VALIDATION_DETAIL,
					List.of(ProblemError.forParameter(e.getParameterName(), "必須です")));
			case HttpMessageNotReadableException e -> unreadable(e);
			case NoResourceFoundException e -> problem(ErrorCode.NOT_FOUND, status, "指定したパスはありません");
			case HttpRequestMethodNotSupportedException e ->
				problem(ErrorCode.METHOD_NOT_ALLOWED, status, "このパスでは " + e.getMethod() + " を使えません");
			case HttpMediaTypeNotAcceptableException e ->
				problem(ErrorCode.NOT_ACCEPTABLE, status, "応答は JSON（application/json）だけで返します");
			case HttpMediaTypeNotSupportedException e ->
				problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE, status, "本文は JSON（application/json）で送ってください");
			default -> problem(ErrorCode.REQUEST_REJECTED, status, "要求を受け付けられません");
		};
	}

	private static ProblemDetail invalid(String detail, List<ProblemError> errors) {
		ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, detail);
		List<ProblemError> sorted = new ArrayList<>(errors);
		sorted.sort(Comparator.comparing((ProblemError e) -> Objects.toString(e.pointer(), ""))
			.thenComparing(e -> Objects.toString(e.parameter(), ""))
			.thenComparing(e -> Objects.toString(e.detail(), "")));
		problem.setProperty("errors", sorted);
		return problem;
	}

	/** {@code @Valid @RequestBody} の違反。項目は JSON Pointer で示す。 */
	private static List<ProblemError> bodyErrors(Errors errors) {
		List<ProblemError> result = new ArrayList<>();
		errors.getFieldErrors()
			.forEach(error -> result.add(ProblemError.forPointer(pointer(error.getField()), message(error))));
		errors.getGlobalErrors().forEach(error -> result.add(ProblemError.forPointer("#", message(error))));
		return result;
	}

	/**
	 * メソッド検証（Spring 6.1 以降）の違反。{@code @RequestParam} の制約に加え、同じメソッドの他の引数に制約があると
	 * {@code @Valid @RequestBody} の違反もここに届くので、どちらも同じ形にする。
	 */
	private static List<ProblemError> methodValidationErrors(HandlerMethodValidationException ex) {
		List<ProblemError> result = new ArrayList<>();
		for (ParameterValidationResult validation : ex.getParameterValidationResults()) {
			if (validation instanceof ParameterErrors body) {
				result.addAll(bodyErrors(body));
				continue;
			}
			String name = parameterName(validation.getMethodParameter());
			validation.getResolvableErrors()
				.forEach(error -> result.add(ProblemError.forParameter(name, message(error))));
		}
		return result;
	}

	/** 壊れた JSON・読めない日付や区分。Jackson の例外が位置を持っていれば pointer を付ける。 */
	private static ProblemDetail unreadable(HttpMessageNotReadableException ex) {
		if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
			String pointer = jackson.getPath()
				.stream()
				.map(ref -> ref.getPropertyName() != null ? escape(ref.getPropertyName())
						: String.valueOf(ref.getIndex()))
				.collect(Collectors.joining("/", "#/", ""));
			Class<?> target = (jackson instanceof MismatchedInputException mismatch) ? mismatch.getTargetType() : null;
			return invalid(VALIDATION_DETAIL, List.of(ProblemError.forPointer(pointer, typeMessage(target))));
		}
		return invalid("本文を JSON として読めません", List.of());
	}

	private static String typeMessage(Class<?> type) {
		if (type == null) {
			return "値の形式が正しくありません";
		}
		if (type.isEnum()) {
			return "次のどれかを指定してください: " + Arrays.stream(type.getEnumConstants())
				.map(Object::toString)
				.collect(Collectors.joining(", "));
		}
		if (Temporal.class.isAssignableFrom(type)) {
			return "日付は yyyy-MM-dd の形で指定してください";
		}
		if (Number.class.isAssignableFrom(type) || type == int.class || type == long.class) {
			return "整数で指定してください";
		}
		return "値の形式が正しくありません";
	}

	/** {@code List<EquipmentCategory>} のような引数は、要素の型でメッセージを決める。 */
	private static Class<?> elementType(MethodParameter parameter) {
		ResolvableType type = ResolvableType.forMethodParameter(parameter);
		if (Collection.class.isAssignableFrom(type.toClass())) {
			return type.asCollection().resolveGeneric(0);
		}
		return type.toClass();
	}

	private static String parameterName(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null && !requestParam.name().isEmpty()) {
			return requestParam.name();
		}
		PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null && !pathVariable.name().isEmpty()) {
			return pathVariable.name();
		}
		String name = parameter.getParameterName();
		return name != null ? name : "arg" + parameter.getParameterIndex();
	}

	private static String pointer(String field) {
		return "#/" + escape(field).replace("[", "/").replace("]", "").replace('.', '/');
	}

	/** JSON Pointer（RFC 6901）のエスケープ。 */
	private static String escape(String name) {
		return name.replace("~", "~0").replace("/", "~1");
	}

	private static String message(MessageSourceResolvable error) {
		return Objects.requireNonNullElse(error.getDefaultMessage(), "値が正しくありません");
	}

}
