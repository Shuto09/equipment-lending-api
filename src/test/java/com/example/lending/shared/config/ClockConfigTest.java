package com.example.lending.shared.config;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

	@Test
	@DisplayName("本番の Clock は DB（timestamptz）と同じマイクロ秒の精度（保存の時の切り上げで日付が変わらないように）")
	void clock_isTruncatedToMicroseconds() {
		Clock clock = new ClockConfig().clock();

		for (int i = 0; i < 100; i++) {
			assertThat(clock.instant().getNano() % 1_000).isZero();
		}
	}

}
