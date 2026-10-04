package com.example.lending.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@link MutableClock} を本体の Clock（ClockConfig#clock）と別の Bean 名で {@code @Primary} を付けて登録する。
 * 同じ名前だと Bean の上書き禁止で起動に失敗し、{@code @Primary} が無いと Clock が 2 つで注入に失敗する。
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(TestData.BASE_TIME);
	}

}
