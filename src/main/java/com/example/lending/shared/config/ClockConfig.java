package com.example.lending.shared.config;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 時刻の出どころを 1 つにする。SQL の中で now() を使わず、サービスがここから 1 操作 1 回だけ読む。
 * テストでは固定の Clock（単体）や進められる Clock（DB を使うテスト）に差し替える。
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	/**
	 * DB（timestamptz）の精度のマイクロ秒に切り捨てた時刻。ナノ秒のまま渡すと保存の時に切り上げられることがあり、
	 * 日本時間の 23:59:59.9999995 以降では、判定に使った日付と保存した日時の日付が食い違う（docs/要件と設計.md §5）。
	 */
	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
	}

}
