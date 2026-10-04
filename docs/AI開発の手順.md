# AI 開発の手順

- この文書は Claude Code（Anthropic の AI）で作成しました。
- 状態: 実装中。実装は 3 回に分けて進める（1: 雛形・共通基盤・備品／2: 貸出／3: E2E・同時実行・README）。ここには、各回に AI が何をどう確かめたかと、人が確かめるべき点を書き足していく。

## 進め方

1. 設計（[要件と設計](要件と設計.md)・[テスト計画](テスト計画.md)）を先に書き、AI による設計レビューの指摘を反映してから実装に入る。
2. 実装は設計どおりに作る。作る途中で設計と違う判断をしたら、同じ回で設計書も直す（各文書の変更履歴に残す）。
3. テストは Docker の要らないもの（単体・Web 層）を手元で回し、Testcontainers を使うものを含む全部は Docker が動く Linux マシンで `./mvnw -B -ntp verify` を回す。

## 実装 1/3: 雛形・共通基盤・備品（2026-10-04）

- 雛形: [技術のバージョンの確認](#技術のバージョンの確認) の生成の URL で start.spring.io から作り、`mybatis-spring-boot-starter`（4.1.0）・`mybatis-spring-boot-starter-test`（4.1.0）・`springdoc-openapi-starter-webmvc-ui`（3.1.1）を `pom.xml` に足した。PostgreSQL の JDBC ドライバは、制約名を `PSQLException` から取り出すのでコンパイル時の依存に変えた。
- 作った物: Flyway の V1（3 表・制約・索引）と demo プロファイルだけの初期データ／ProblemDetail の例外ハンドラ・`ErrorCode`・X-User-Id の認証（interceptor と引数リゾルバ）・ページングと並び替えの型・LIKE のエスケープ・制約名の取り出し／備品の登録・検索・詳細・更新・廃棄（コントローラ・サービス・MyBatis の XML）／GitHub Actions の設定。
- テスト: 単体（`EquipmentServiceTest`・`SortParserTest`・`LikePatternsTest`）・機械検査（`MapperXmlSafetyTest`）・Web 層（`EquipmentControllerTest`・`ApiExceptionHandlerTest`）・マッパー（`EquipmentMapperTest`・`SchemaConstraintTest`・`UserMapperTest`。本物の PostgreSQL 17）・文脈の起動（`contextLoads`）。
- 確かめた結果: 2026-10-04、Docker の動く Linux マシンで `eclipse-temurin:25-jdk` のコンテナの中から `./mvnw -B -ntp verify` を実行し、テスト 174 件がすべて成功（失敗・スキップ 0）。GitHub Actions ではまだ流していない（リポジトリを公開した後に確かめる）。
- 実装の途中で分かり、設計書を直したこと（詳細は各文書の変更履歴 v0.3）:
  - Boot 4.1.1 と MyBatis Starter 4.1.0 は組めた（未決事項 Q1 の解決）。
  - MyBatis の `<if test>`（OGNL）で入れ子の record の項目を読む時は `sort.key()` のようにメソッド呼び出しで書く。
  - Jackson 3 は既定で項目名の辞書順に並べるので、ProblemDetail の項目が `detail`・`instance`… の順になっていた。設定で切り、RFC 9457 の例と同じ並びにした（実際の応答を出力して気づいた）。
  - 406・415 は 401 より先になると設計に書いていたが、マッピングに `produces`・`consumes` を書いていないので、実際は認証の後に決まる。設計の文を直した（405 は認証より先のまま）。
- 人が確かめるべき点:
  - [ ] `mapper/equipment/EquipmentMapper.xml` の検索の SQL（貸出状態の `CASE`・並び替えの `<choose>`・件数の文と一覧の文の条件の共有）が意図どおりに読めるか。
  - [ ] 入力検証の文（Hibernate Validator の日本語の文、例「null は許可されていません」）を、利用者向けにすべて自分の文へ書き換えるか。
  - [ ] PATCH で変更する項目が 0 個の時の `errors` の `pointer` が `#/anyFieldPresent`（本文に無い名前）になる点を許すか（要件と設計 §6）。
  - [ ] `compose.yaml` の DB の利用者名・パスワードは手元専用の値であること。

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
- Boot 4 で変わった点（実装時の注意）: スターターが細かく分かれた（例 `spring-boot-starter-webmvc`・`spring-boot-starter-flyway`）、JSON は Jackson 3（3.1.5・パッケージ `tools.jackson`）、テストの Bean 差し替えは `@MockitoBean`、HTTP のテストは `MockMvcTester` と `RestTestClient` が使える。`RestTestClient` の自動設定は `spring-boot-resttestclient`（`spring-boot-starter-webmvc-test` に含まれる）にあり、`@AutoConfigureRestTestClient` を付けた時だけ読まれる（4.1.1 の jar の `META-INF/spring/…AutoConfigureRestTestClient.imports` で確認・`spring.factories` は無い）。
