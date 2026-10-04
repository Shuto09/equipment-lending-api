package com.example.lending.shared.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

	@Test
	@DisplayName("% は文字として扱う（100% で 100 から始まる全件に当たらない）")
	void percentIsEscaped() {
		assertThat(LikePatterns.containing("100%")).isEqualTo("%100\\%%");
	}

	@Test
	@DisplayName("_ は文字として扱う")
	void underscoreIsEscaped() {
		assertThat(LikePatterns.containing("a_b")).isEqualTo("%a\\_b%");
	}

	@Test
	@DisplayName("エスケープ文字の \\ 自体もエスケープする")
	void backslashIsEscaped() {
		assertThat(LikePatterns.containing("a\\b")).isEqualTo("%a\\\\b%");
	}

	@Test
	@DisplayName("前後の空白は除く")
	void stripsSurroundingSpaces() {
		assertThat(LikePatterns.containing("  pc ")).isEqualTo("%pc%");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "\t" })
	@DisplayName("空・空白だけは絞り込みなし（null）")
	void blankMeansNoFilter(String keyword) {
		assertThat(LikePatterns.containing(keyword)).isNull();
	}

}
