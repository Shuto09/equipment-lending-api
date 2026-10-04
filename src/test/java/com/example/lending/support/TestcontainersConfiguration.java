package com.example.lending.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

/**
 * テスト用の PostgreSQL（手元の compose.yaml と同じ postgres:17）。コンテナはアプリケーション文脈と一緒に起動・停止し、
 * 同じ設定のテストクラスは Spring の文脈キャッシュで文脈とコンテナを共有する（docs/テスト計画.md §1）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
