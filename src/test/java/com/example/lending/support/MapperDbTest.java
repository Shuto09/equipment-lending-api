package com.example.lending.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;

import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * マッパー（DB）のテスト: MyBatis と Flyway と本物の PostgreSQL（Testcontainers）だけの文脈。各テストはロールバックされる。
 * 設定をこの注釈 1 つにそろえ、同じ文脈（＝同じコンテナ）を全マッパーのテストで共有する（docs/テスト計画.md §1）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("docker")
@Import({ TestcontainersConfiguration.class, TestData.class })
public @interface MapperDbTest {

}
