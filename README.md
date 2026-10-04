# equipment-lending-api — 社内備品の貸出管理 API（見本）

社内の備品（ノート PC・モニター・プロジェクターなど）の登録・検索・貸出・返却と、延滞の一覧を扱う REST API です。
業務ロジックの設計、SQL（MyBatis の動的 SQL）、トランザクションと排他制御、テストの組み立てを、短い時間で読める量で見せるための**見本**です。機能の多さは目標にしていません。
認証はヘッダで利用者を名乗るデモ方式なので、このままでは本番に使えません（[本番で使うなら](#本番で使うなら)）。

> このリポジトリのコード・テスト・文書は Claude Code（Anthropic の AI）で作成しました。作り方は [docs/AI開発の手順.md](docs/AI開発の手順.md)

## 10 分で読むなら

| 順 | 読む所 | 分かること |
|---|---|---|
| 1 | 下の [設計の要点](#設計の要点) | 何をどこで守っているか |
| 2 | [`LoanService#lend`](src/main/java/com/example/lending/loan/LoanService.java#L50-L101) | 貸出の判定の順番と、悲観ロックの順番 |
| 3 | [`EquipmentMapper.xml`](src/main/resources/mapper/equipment/EquipmentMapper.xml#L45-L99) | 動的 SQL・並び替えの許可リスト・件数と一覧の条件の共有 |
| 4 | [`ConcurrentLendingTest`](src/test/java/com/example/lending/loan/ConcurrentLendingTest.java) | 二重貸出などの競合を、実際に並列で起こすテスト |
| 5 | [要件と設計 §13 採らなかった案](docs/要件と設計.md#13-採らなかった案と理由) | JPA ではなく MyBatis にした理由など |

## 設計の要点

1. **二重貸出は DB の制約で止める。**
   未返却の行だけを対象にした部分一意インデックス（[`V1__create_tables.sql`](src/main/resources/db/migration/V1__create_tables.sql#L43-L45)）で、1 台に未返却の貸出は 1 件までになります。アプリの判定をすり抜けても破れません。
   違反は制約名で見分け、`uq_loans_open_equipment` の時だけ 409 に変換します（[`DbConstraints`](src/main/java/com/example/lending/shared/sql/DbConstraints.java#L37-L51)・[`LoanService`](src/main/java/com/example/lending/loan/LoanService.java#L91-L99)）。ほかの制約は 500 のままにして、原因を取り違えないようにしています。
2. **判定の順番とロックの順番を決めて守る。**
   貸出は「期限の範囲 → 利用者行のロック → 備品行のロック → 廃棄済み → 貸出中 → 延滞あり → 件数の上限」の順です（[`LoanService#lend`](src/main/java/com/example/lending/loan/LoanService.java#L50-L101)）。
   1 人 3 件までの上限は、数える対象の行をロックできない（これから入る行はまだ無い）ので、利用者行のロックで直列化します。廃棄と貸出の競合は備品行のロックで直列化します。ロックは常に「利用者 → 備品」の順なのでデッドロックしません（[要件と設計 §8](docs/要件と設計.md#8-排他制御二重貸出をどう防ぐか)）。
3. **SQL はすべて XML に書き、動的 SQL は許可リストで組む。**
   検索条件は `<where>`・`<if>`・`<foreach>` で組み、並び替えは enum の名前を `<choose>` で列に対応させます。利用者の入力が SQL の文字列に入る経路がありません（[`EquipmentMapper.xml`](src/main/resources/mapper/equipment/EquipmentMapper.xml#L61-L93)・[`SortParser`](src/main/java/com/example/lending/shared/paging/SortParser.java)）。
   マッパー XML に `${}` と DB の時刻関数が無いこと、Java に SQL の注釈が無いことは [`MapperXmlSafetyTest`](src/test/java/com/example/lending/MapperXmlSafetyTest.java) で機械的に確かめます。件数と一覧は同じ条件の断片を使い、REPEATABLE READ で同じ時点から読みます。
4. **時刻の出どころを 1 つにする。**
   SQL の中で `now()` を使わず、サービスが 1 回の操作で 1 回だけ `Clock` から読み、日本時間の業務日付にして判定と保存の両方に使います（[`LoanService#lend`](src/main/java/com/example/lending/loan/LoanService.java#L62-L65)・[`LendingProperties`](src/main/java/com/example/lending/shared/config/LendingProperties.java)）。
   延滞は保存せず「返却期限 < 今日」で毎回求めます。テストでは時計を固定したり進めたりします。
5. **エラー応答を ProblemDetail（RFC 9457）にそろえる。**
   すべての失敗応答に機械で見分ける `code` が付きます。`type` は入力検証・認証・業務エラーではコードごとの URI、HTTP の状態以上の意味が無いもの（404・405 など）では `about:blank` です（[`ErrorCode`](src/main/java/com/example/lending/shared/error/ErrorCode.java)・[`ApiExceptionHandler`](src/main/java/com/example/lending/shared/error/ApiExceptionHandler.java)）。409（対象の今の状態と衝突）と 422（業務規則に反する）を使い分けます。
   500 では例外の詳細を応答に出さず、一般的な文と `traceId` を返します（詳細は同じ `traceId` でログにだけ出す）。認証は引数の解決より前の interceptor で行うので、401 は入力検証の 400 より先に返ります（[`AuthenticationInterceptor`](src/main/java/com/example/lending/user/AuthenticationInterceptor.java)）。
6. **競合を実際に起こしてテストする。**
   8 本のスレッドを `CountDownLatch` でそろえ、本物の PostgreSQL 17 に同時に書き込みます（[`ConcurrentLendingTest`](src/test/java/com/example/lending/loan/ConcurrentLendingTest.java#L158-L190)）。
   ロックや制約を 1 か所ずつ外した写しで、狙ったテストが赤になることも確かめました（結果は [テスト計画 §5](docs/テスト計画.md#5-同時実行テスト)）。

## 使っている技術

| 部品 | バージョン |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1（Web MVC・Validation・Actuator） |
| MyBatis Spring Boot Starter | 4.1.0（MyBatis 3.5.19）。SQL は XML のマッパーに書く |
| PostgreSQL | 17（`postgres:17` の公式イメージ） |
| Flyway | 12.4.0（Spring Boot 4.1.1 が管理するバージョン。理由は [要件と設計 §14](docs/要件と設計.md#14-使う技術とバージョン2026-10-04-に確認)） |
| springdoc-openapi | 3.1.1（OpenAPI と Swagger UI） |
| テスト | JUnit Jupiter 6.0.3（書き方は JUnit 5 と同じ）・AssertJ・Mockito・Testcontainers 2.0.5 |
| ビルド・CI | Maven Wrapper（Maven 3.9.16）・GitHub Actions |

バージョンは 2026-10-04 に確かめました。確かめ方は [AI開発の手順](docs/AI開発の手順.md#技術のバージョンの確認) にあります。

## API

`/api/**` のすべての API に、利用者の ID を入れた `X-User-Id` ヘッダが要ります（デモ方式の認証）。成功は `application/json`、失敗は `application/problem+json` で返します。

| # | メソッド | パス | 誰が | 何をする |
|---|---|---|---|---|
| 1 | POST | `/api/equipment` | ADMIN | 備品を登録する |
| 2 | GET | `/api/equipment` | 全員 | 備品を探す（キーワード・区分・貸出状態・並び替え・ページ） |
| 3 | GET | `/api/equipment/{id}` | 全員 | 備品の詳細（貸出中なら借り手の表示名と返却期限） |
| 4 | PATCH | `/api/equipment/{id}` | ADMIN | 名称・区分・備考を直す（`version` で楽観ロック） |
| 5 | POST | `/api/equipment/{id}/dispose` | ADMIN | 廃棄する（貸出中は不可） |
| 6 | POST | `/api/loans` | 全員 | 借りる（返却期限は今日から 14 日後まで） |
| 7 | GET | `/api/loans` | 全員 | 貸出の一覧（MEMBER は自分の分だけ） |
| 8 | GET | `/api/loans/{id}` | 借りた本人か ADMIN | 貸出の詳細 |
| 9 | POST | `/api/loans/{id}/return` | 借りた本人か ADMIN | 返却する |
| 10 | GET | `/api/loans/overdue` | ADMIN | 延滞の一覧（延滞日数の多い順。借り手のメールアドレスを含む） |

入力・応答・エラーの詳しい形は [要件と設計 §6・§7](docs/要件と設計.md#6-api-一覧) と、起動した後の Swagger UI にあります。

## 動かし方

必要なもの: JDK 21 以上と Docker（`docker compose` が使えること）。Windows の PowerShell では `./mvnw` を `.\mvnw.cmd` に読み替えてください。

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

- [`compose.yaml`](compose.yaml) の PostgreSQL 17 を、Spring Boot（`spring-boot-docker-compose`）が起動して接続先も設定します。アプリを止めるとコンテナも止まります。
- `demo` プロファイルは、架空の初期データ（利用者 4 人・備品 8 台・未返却の貸出 2 件。[`R__demo_data.sql`](src/main/resources/db/demo/R__demo_data.sql)）も入れます。一度 `demo` で起動した DB は、以後も `demo` を付けて起動してください（適用済みの初期データのマイグレーションが見つからない状態を作らないため）。
- 起動すると次が開きます。

| 何 | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html （`/swagger-ui/index.html` に転送されます） |
| OpenAPI（JSON） | http://localhost:8080/v3/api-docs |
| ヘルスチェック | http://localhost:8080/actuator/health （Actuator は health だけを公開） |

新しい DB に初期データを入れた時の利用者（`X-User-Id` に使う値）:

| `X-User-Id` | 表示名 | 役割 |
|---|---|---|
| 1 | 鈴木 一郎 | ADMIN |
| 2 | 山田 太郎 | MEMBER |
| 3 | 佐藤 花子 | MEMBER |
| 4 | 田中 次郎 | MEMBER |

初期データの日付は 2026 年 9〜10 月で固定です。後の日付で動かすと、山田さんと佐藤さんの貸出は延滞として扱われ、2 人は新しく借りられません（422 `BORROWER_HAS_OVERDUE`）。田中さんは貸出を持たないので、いつでも借りる操作を試せます。

### curl の例

bash などのシェルで、新しい DB に `demo` の初期データを入れて起動した直後に、上から順に流す前提です（id は初期データの並びで決まります）。借りる例の `dueDate` は、今日から 14 日後までの日付に置き換えてください。

```sh
# 空いている備品を、名前の降順で探す（山田）
curl -s 'http://localhost:8080/api/equipment?keyword=pc&availability=AVAILABLE&sort=name,desc' -H 'X-User-Id: 2'
# 備品の詳細。貸出中なら借り手の表示名と返却期限が出る（佐藤）
curl -s http://localhost:8080/api/equipment/2 -H 'X-User-Id: 3'
# 備品を登録する → 201 と Location（ADMIN）
curl -s -i -X POST http://localhost:8080/api/equipment -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
  -d '{"assetCode": "PC-0004", "name": "ノート PC 14 インチ", "category": "LAPTOP"}'
# 備考を消す（空文字を送る）。version は読んだ時の値（ADMIN）
curl -s -X PATCH http://localhost:8080/api/equipment/3 -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
  -d '{"remarks": "", "version": 0}'
# 廃棄する（ADMIN）
curl -s -X POST http://localhost:8080/api/equipment/7/dispose -H 'X-User-Id: 1'
# 借りる → 201（田中）
curl -s -i -X POST http://localhost:8080/api/loans -H 'X-User-Id: 4' -H 'Content-Type: application/json' \
  -d '{"equipmentId": 1, "dueDate": "2026-10-11"}'
# 同じ備品を借りる → 409 EQUIPMENT_ALREADY_LENT（佐藤）
curl -s -i -X POST http://localhost:8080/api/loans -H 'X-User-Id: 3' -H 'Content-Type: application/json' \
  -d '{"equipmentId": 1, "dueDate": "2026-10-11"}'
# 自分の未返却の貸出を、返却期限の近い順に（田中。MEMBER は自分の分だけ）
curl -s 'http://localhost:8080/api/loans?state=OPEN&sort=dueDate,asc' -H 'X-User-Id: 4'
# 貸出の詳細と返却（借りた本人）
curl -s http://localhost:8080/api/loans/3 -H 'X-User-Id: 4'
curl -s -X POST http://localhost:8080/api/loans/3/return -H 'X-User-Id: 4'
# 延滞の一覧（ADMIN）
curl -s http://localhost:8080/api/loans/overdue -H 'X-User-Id: 1'
# ヘッダが無い → 401
curl -s -i http://localhost:8080/api/equipment
```

借りた時の応答（201）と、同じ備品を別の人が借りようとした時の応答（409）の実例です（整形しています）。

```json
{
  "id": 3,
  "equipment": { "id": 1, "assetCode": "PC-0001", "name": "ノート PC 14 インチ" },
  "borrower": { "id": 4, "displayName": "田中 次郎" },
  "lentAt": "2026-10-04T16:27:55.26448+09:00",
  "dueDate": "2026-10-11",
  "returnedAt": null,
  "state": "LENT",
  "returnedLate": false
}
```

```json
{
  "type": "https://example.com/problems/equipment-already-lent",
  "title": "貸出中の備品です",
  "status": 409,
  "detail": "備品 PC-0001 は貸出中です",
  "instance": "/api/loans",
  "code": "EQUIPMENT_ALREADY_LENT"
}
```

この節の curl は、2026-10-04 に上の `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` で起動したアプリ（新しい DB・`demo` の初期データ）に対して上から順に 1 回ずつ実行し、状態コードと応答を確かめました。確かめた環境の都合で、環境変数をいくつか足しています（待ち受けのアドレスとポートなど。詳細は [AI開発の手順](docs/AI開発の手順.md#readme-の例の確かめ方)）。

## テストの回し方

| 目的 | コマンド | Docker |
|---|---|---|
| 単体・Web 層・機械的な検査だけ（速い） | `./mvnw -B -ntp test -DexcludedGroups=docker` | 不要 |
| 全部（本物の PostgreSQL 17 を Testcontainers で起動） | `./mvnw -B -ntp verify` | 要る |

GitHub Actions（[`ci.yml`](.github/workflows/ci.yml)）は、push と pull request で `./mvnw -B -ntp verify` を流します。

| 層 | 主なテストクラス | 件数 |
|---|---|---|
| 単体（Mockito・DB なし） | `LoanServiceTest`・`EquipmentServiceTest`・`SortParserTest`・`LikePatternsTest` | 71 |
| 機械的な検査 | `MapperXmlSafetyTest`（`${}`・時刻関数・SQL の注釈が 0 件） | 31 |
| Web 層（`@WebMvcTest`） | `EquipmentControllerTest`・`LoanControllerTest`・`ApiExceptionHandlerTest` | 93 |
| マッパー（本物の PostgreSQL） | `EquipmentMapperTest`・`LoanMapperTest`・`UserMapperTest`・`SchemaConstraintTest` | 46 |
| アプリ全体（本物の PostgreSQL） | `ConcurrentLendingTest`・`LendingApiEndToEndTest`・`OpenApiContractTest`・`PublicEndpointsTest`・起動確認 | 73 |

実測（2026-10-04）:

- `./mvnw -B -ntp verify`: 314 件すべて成功（失敗・エラー・スキップ 0）。Maven の所要時間 27.4 秒（コンパイルと Testcontainers の起動を含む）。Docker の動く Linux マシン（Intel Core i5-6400・4 コア）の `eclipse-temurin:25-jdk` のコンテナの中で実行。
- `./mvnw -B -ntp test -DexcludedGroups=docker`: 195 件すべて成功。Maven の所要時間 13.0 秒（本体はコンパイル済みの状態から。テストのコンパイルを含む）。Windows 11・JDK 21 で実行。
- 受け入れ条件（AC-01〜AC-24）とテストの対応は [テスト計画 §6](docs/テスト計画.md#6-受け入れ条件とテストの対応) にあります。テストの `@DisplayName` にも AC の番号を付けています。

## SQL の実行計画

架空のデータ（利用者 1,000 人・備品 5,000 台・貸出 51,500 件）で `EXPLAIN (ANALYZE, BUFFERS)` を取りました（2026-10-04・PostgreSQL 17.11）。

| 文 | 読み方 | 実行時間 |
|---|---|---|
| 備品検索の一覧（キーワード・貸出状態・並び替え） | 備品は順次走査（`'%pc%'` の `ILIKE` は B-tree 索引を使えない）。未返却の貸出は部分一意インデックスから読んで結合 | 6.719 ms |
| 延滞一覧（20 件） | 部分索引 `ix_loans_open_due_date` を並び順のまま読み、20 行で止まる | 0.178 ms |

データの作り方・実行したコマンド・計画の全文は [docs/実行計画の実測.md](docs/実行計画の実測.md) にあります。数値はこの 1 回の実測で、性能の目標ではありません。

## ディレクトリ構成

```
├── compose.yaml                       手元で使う PostgreSQL 17
├── docs/
│   ├── 要件と設計.md                   業務ルール・データモデル・API・排他制御・採らなかった案
│   ├── テスト計画.md                   テストの層・同時実行のテスト・受け入れ条件との対応
│   ├── 実行計画の実測.md                EXPLAIN の結果（explain/ にデータと SQL）
│   └── AI開発の手順.md                 Claude Code でどう作ったか
├── src/main/java/com/example/lending/
│   ├── equipment/                     備品（コントローラ・サービス・マッパー・要求と応答の record）
│   ├── loan/                          貸出・返却・延滞
│   ├── user/                          デモ方式の認証（X-User-Id）と利用者
│   └── shared/                        エラー応答・ページング・SQL の補助・設定
├── src/main/resources/
│   ├── mapper/                        MyBatis のマッパー XML（SQL はすべてここ）
│   ├── db/migration/                  Flyway（テーブル・制約・索引）
│   └── db/demo/                       demo プロファイルだけで読む架空の初期データ
├── src/test/java/com/example/lending/ テスト（層ごとの内訳は上の表）
└── CLAUDE.md                          このリポジトリで AI に作業させる時の決まり
```

規模（2026-10-04 に `wc -l` で数えた値。コメントと空行を含む）: 本体の Java 55 ファイル・1,896 行、マッパー XML 3 ファイル・326 行、テストの Java 25 ファイル・3,628 行。

## 本番で使うなら

この見本では範囲の外にしたものです（[要件と設計 §2・§12](docs/要件と設計.md#12-認証デモ方式と非機能)）。

- **認証**: `X-User-Id` は誰でも他人を名乗れます。Spring Security の OAuth 2.0 リソースサーバー（OIDC のアクセストークン）に置き換えます。差し替えるのは interceptor と引数のリゾルバだけで、サービスは変わりません。
- **エラーの `type`**: `https://example.com/problems/…` は仮の値です。自分のドメインにして説明のページを置きます。
- **OpenAPI と Swagger UI**: 公開しない環境では `springdoc.api-docs.enabled=false`・`springdoc.swagger-ui.enabled=false` で止めます。
- **DB の接続情報**: `compose.yaml` の値は手元専用です。本番は環境変数などで渡します。

## ライセンス

[MIT](LICENSE)
