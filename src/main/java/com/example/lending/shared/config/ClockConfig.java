package com.example.lending.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 時刻の出どころを 1 つにする。SQL の中で now() を使わず、サービスがここから 1 操作 1 回だけ読む。
 * テストでは固定の Clock（単体）や進められる Clock（DB を使うテスト）に差し替える。
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
