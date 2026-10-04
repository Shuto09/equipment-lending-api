package com.example.lending.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ProblemDetail の errors の 1 件。本文の項目は pointer（JSON Pointer）、クエリパラメータは parameter で示す。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemError(String pointer, String parameter, String detail) {

	public static ProblemError forPointer(String pointer, String detail) {
		return new ProblemError(pointer, null, detail);
	}

	public static ProblemError forParameter(String parameter, String detail) {
		return new ProblemError(null, parameter, detail);
	}

}
