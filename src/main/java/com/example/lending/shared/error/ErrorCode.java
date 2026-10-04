package com.example.lending.shared.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * エラーの code ↔ HTTP 状態 ↔ type ↔ title の対応表の正本（docs/要件と設計.md §7）。
 * title が null のものは HTTP の状態以上の意味が無いので、type は about:blank、title は状態の名前になる。
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "入力が正しくありません"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "利用者を確認できません"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "この操作の権限がありません"),
	EQUIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "備品が見つかりません"),
	LOAN_NOT_FOUND(HttpStatus.NOT_FOUND, "貸出が見つかりません"),
	ASSET_CODE_DUPLICATED(HttpStatus.CONFLICT, "管理番号が登録済みです"),
	EQUIPMENT_ALREADY_LENT(HttpStatus.CONFLICT, "貸出中の備品です"),
	EQUIPMENT_DISPOSED(HttpStatus.CONFLICT, "廃棄済みの備品です"),
	EQUIPMENT_IN_USE(HttpStatus.CONFLICT, "貸出中の備品は廃棄できません"),
	LOAN_ALREADY_RETURNED(HttpStatus.CONFLICT, "返却済みの貸出です"),
	VERSION_CONFLICT(HttpStatus.CONFLICT, "ほかの更新と競合しました"),
	DUE_DATE_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "返却期限が範囲の外です"),
	BORROWER_HAS_OVERDUE(HttpStatus.UNPROCESSABLE_CONTENT, "延滞中の貸出があります"),
	LOAN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "貸出の上限に達しています"),

	// ここから下は HTTP の状態以上の意味を持たない（type は about:blank）
	NOT_FOUND(HttpStatus.NOT_FOUND, null),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, null),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, null),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, null),
	/** 上のどれにも当たらない Spring MVC の 4xx。状態は元の例外のものを使う（ここの 400 は代表値）。 */
	REQUEST_REJECTED(HttpStatus.BAD_REQUEST, null),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, null);

	/** RFC 9457 の例と同じ example.com ドメインの仮の基底 URI。実運用では自分のドメインにして説明ページを置く（§16 Q3）。 */
	private static final String TYPE_BASE = "https://example.com/problems/";

	private static final URI ABOUT_BLANK = URI.create("about:blank");

	private final HttpStatus status;

	private final String title;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	/** 固有の title。null なら HTTP の状態の名前を使う。 */
	public String title() {
		return title;
	}

	public URI type() {
		if (title == null) {
			return ABOUT_BLANK;
		}
		return URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
	}

}
