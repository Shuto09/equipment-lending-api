package com.example.lending.shared.validation;

/**
 * 文字列の入力を検証する正規表現（{@code @Pattern} の regexp。docs/要件と設計.md §6）。
 * <ul>
 * <li>Java の正規表現の {@code \s} は既定で ASCII の空白だけなので、{@code (?U)} を付けて全角スペース（U+3000）や NBSP も空白として扱う。</li>
 * <li>制御文字を弾くのは、PostgreSQL が NUL（U+0000）を含む文字列を受け付けず、入力の誤りが 400 ではなく 500 になるのを防ぐため。</li>
 * </ul>
 */
public final class TextPatterns {

	/** 空白以外の文字を 1 つ以上含む（空白だけの名称を弾く）。改行の有無は {@link #NO_CONTROL} で別に見る。 */
	public static final String NOT_BLANK = "(?Us).*\\S.*";

	/** 制御文字（改行・タブ・NUL など。Unicode の Cc）を含まない。1 行で書く値（名称・キーワード）に使う。 */
	public static final String NO_CONTROL = "[^\\p{Cc}]*";

	/** 改行（LF・CR）とタブ以外の制御文字を含まない。複数行を許す値（備考）に使う。 */
	public static final String NO_CONTROL_EXCEPT_LINE_BREAKS = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]*";

	private TextPatterns() {
	}

}
