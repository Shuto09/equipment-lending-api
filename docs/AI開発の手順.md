# AI 開発の手順

- この文書は Claude Code（Anthropic の AI）で作成しました。
- 状態: 実装中。実装は 3 回に分けて進める（1: 雛形・共通基盤・備品／2: 貸出と同時実行のテスト／3: E2E・OpenAPI の一致の検査・README）。ここには、各回に AI が何をどう確かめたかと、人が確かめるべき点を書き足していく。

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

## 実装 2/3: 貸出（2026-10-04）

- 作った物: 貸出・返却・貸出一覧・貸出の詳細・延滞一覧（`loan` パッケージのコントローラ・サービス・MyBatis の XML）と、利用者行のロック（`UserMapper#lockById`）。
- 進め方: 設計の §4（判定の順番）・§8（ロックの順番）・§9（トランザクションの境界）を、そのまま `LoanService#lend` の文の順番にした。順番は Javadoc に番号付きで書き、単体テストで「複数に当たる時は最初のエラーを返す」「ロックは利用者 → 備品」を確かめる。
- テスト（追加分）:
  - 単体 `LoanServiceTest`（33 件）: 業務ルールの全分岐、判定の順番、ロックの順番、`Clock` を 1 回だけ読むこと、返却の遅れを日本時間の日付で判定すること、一意制約違反を制約名で変換すること。
  - Web 層 `LoanControllerTest`（23 件）。
  - マッパー `LoanMapperTest`（11 件）と `UserMapperTest` の 1 件（本物の PostgreSQL 17）。
  - 同時実行 `ConcurrentLendingTest`（62 件）: CT-1・CT-3・CT-4 を 20 回ずつ、CT-2・CT-5 を 1 回ずつ。8 本のスレッドを `CountDownLatch` でそろえてから一斉にサービスを呼び、結果の集計を丸ごと比べる。
- 確かめた結果: 2026-10-04、Docker の動く Linux マシンで `eclipse-temurin:25-jdk` のコンテナの中から `./mvnw -B -ntp verify` を実行し、テスト 304 件がすべて成功した（失敗・スキップは 0）。GitHub Actions ではまだ流していない。
- 排他制御が本当に効いているかの確認: ロックと制約を 1 か所ずつ外した写しで同時実行のテストを流し、狙ったテストが赤になることを確かめた（M1〜M4。結果は [テスト計画 §5](テスト計画.md#5-同時実行テスト)）。本体のコードは書き換えていない。
- 実装の途中で決め、設計書に書き足したこと（要件と設計 v0.4・テスト計画 v0.4）:
  - 応答の `state`（`LENT`/`OVERDUE`/`RETURNED`）と一覧の絞り込みの `state`（`OPEN`/`RETURNED`/`ALL`）は値の種類が違うので、enum を分けた（`LoanState`・`LoanStateFilter`）。
  - 応答の `state` は絞り込みに使わないので、SQL ではなく Java で求めた（備品の貸出状態は絞り込みに使うので SQL の `CASE`）。
  - 未返却の貸出の `returnedLate` は `false` にした（`null` にしない）。
- 人が確かめるべき点:
  - [ ] `LoanService#lend` の判定の順番とロックの順番が、業務として意図どおりか（要件と設計 §4・§8）。
  - [ ] 同時実行のテストの繰り返し 20 回で足りるか。M1 では 20 回中 19 回だけ赤になった。1 回の実行で見逃すことは少ないが、0 ではない。
  - [ ] 未返却の貸出の `returnedLate` を `false` で返す点。
  - [ ] 貸出の時に利用者行が見つからなければ 401 にしている点。利用者を消す API は無いので、起きない想定。

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
