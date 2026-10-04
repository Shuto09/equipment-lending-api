package com.example.lending.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * アプリ全体のテスト（E2E・同時実行・OpenAPI）: 本物の PostgreSQL と、進められる時計（{@link MutableClock}）。
 * COMMIT するので、各テストの前に {@link TestData#reset()} を呼ぶ。
 * この注釈のクラスに {@code @MockitoBean} などクラスごとの差分を足さない（文脈が分かれ、コンテナが増える）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("docker")
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class, TestData.class })
public @interface AppDbTest {

}
