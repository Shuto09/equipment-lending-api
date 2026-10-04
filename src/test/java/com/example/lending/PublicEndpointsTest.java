package com.example.lending;

import com.example.lending.support.AppDbTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /api/** の外に出すもの（docs/要件と設計.md §12）: Swagger UI と、Actuator の health だけ。どれも X-User-Id は要らない。
 */
@AppDbTest
class PublicEndpointsTest {

	@Autowired
	private RestTestClient client;

	@Test
	@DisplayName("Swagger UI の画面が開ける（README に書いたパス）")
	void swaggerUi_isServed() {
		String html = client.get()
			.uri("/swagger-ui/index.html")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(html).contains("swagger-ui");
	}

	@Test
	@DisplayName("Actuator の health は公開し、DB につながっていれば UP")
	void health_isUp() {
		client.get().uri("/actuator/health").exchange().expectStatus().isOk().expectBody().jsonPath("$.status")
			.isEqualTo("UP");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/info" })
	@DisplayName("health 以外の Actuator は公開しない（404）")
	void otherActuatorEndpoints_areNotExposed(String path) {
		client.get().uri(path).exchange().expectStatus().isNotFound();
	}

}
