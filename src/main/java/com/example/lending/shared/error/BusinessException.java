package com.example.lending.shared.error;

import java.util.List;

/**
 * 業務エラー。投げるとトランザクションはロールバックされ、ApiExceptionHandler が ProblemDetail にする。
 * メッセージは応答の detail になるので、利用者に見せてよい日本語の説明だけを入れる。
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode code;

	private final List<ProblemError> errors;

	public BusinessException(ErrorCode code, String detail) {
		this(code, detail, List.of());
	}

	public BusinessException(ErrorCode code, String detail, List<ProblemError> errors) {
		super(detail);
		this.code = code;
		this.errors = List.copyOf(errors);
	}

	/** クエリパラメータの値が正しくない（400 VALIDATION_FAILED）。 */
	public static BusinessException invalidParameter(String parameter, String detail) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_DETAIL,
				List.of(ProblemError.forParameter(parameter, detail)));
	}

	public ErrorCode code() {
		return code;
	}

	public List<ProblemError> errors() {
		return errors;
	}

}
