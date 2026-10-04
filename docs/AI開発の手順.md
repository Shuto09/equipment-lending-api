# AI 開発の手順

- この文書は Claude Code（Anthropic の AI）で作成しました。
- 状態: 実装の前。進め方と、人が確かめるべき点は実装の段で書き足す。今あるのは、設計で決めた技術のバージョンを確かめた手順だけ。

## 技術のバージョンの確認

2026-10-04 に確認した。結果と判断は [要件と設計 §14](要件と設計.md#14-使う技術とバージョン2026-10-04-に確認)。

```sh
# Boot のバージョンと依存の対応表（mybatis の versionRange が [4.0.0,4.1.0-M1)）
curl -s -H 'Accept: application/vnd.initializr.v2.3+json' https://start.spring.io/metadata/client
# 4.1.1 と mybatis を同時に選ぶと 400:
#   "Dependency 'mybatis' is not compatible with Spring Boot 4.1.1"
curl -s 'https://start.spring.io/starter.zip?type=maven-project&bootVersion=4.1.1&javaVersion=21&dependencies=web,mybatis'
# MyBatis Starter 4.1.0 が組まれた Boot のバージョン
curl -s https://repo1.maven.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot/4.1.0/mybatis-spring-boot-4.1.0.pom | grep spring-boot.version
# Boot 4.1.1 が管理するバージョン
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom | grep -E 'flyway|testcontainers|junit-jupiter'
```

- 生成の URL（mybatis 以外で生成し、`mybatis-spring-boot-starter` と `mybatis-spring-boot-starter-test` の 4.1.0 を `pom.xml` に足す）:
  `https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.1&javaVersion=21&groupId=com.example&artifactId=equipment-lending-api&name=equipment-lending-api&packageName=com.example.lending&dependencies=web,validation,actuator,flyway,postgresql,testcontainers,docker-compose`
- Boot 4 で変わった点（実装時の注意）: スターターが細かく分かれた（例 `spring-boot-starter-webmvc`・`spring-boot-starter-flyway`）、JSON は Jackson 3（3.1.5・パッケージ `tools.jackson`）、テストの Bean 差し替えは `@MockitoBean`、HTTP のテストは `MockMvcTester` と `RestTestClient` が使える。
