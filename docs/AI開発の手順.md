# AI 開発の手順

- この文書は Claude Code（Anthropic の AI）で作成しました。
- このリポジトリのコード・テスト・文書は、すべて Claude Code で作りました。ここには、どの順で何を作り、どう確かめたかを、記録（git の履歴・各文書の変更履歴・テストの実行結果）に残っている範囲で書きます。記録の無いこと（人が書いた・人がレビューした・本番で動かした など）は書きません。
- AI に作業させる時の決まりは [CLAUDE.md](../CLAUDE.md) にまとめています。

## 工程

| # | 工程 | 中身 | 状態（2026-10-04 時点） |
|---|---|---|---|
| 1 | 要件と設計 | [要件と設計](要件と設計.md)・[テスト計画](テスト計画.md) の v0.1 を、実装の前に書く | 済 |
| 2 | 設計レビュー | AI（Claude Code）が設計を見直し、指摘を設計書に反映する（v0.2） | 済 |
| 3 | 実装 1/3 | 雛形・共通基盤・備品 | 済 |
| 4 | 実装 2/3 | 貸出・返却・延滞と、同時実行のテスト | 済 |
| 5 | 実装 3/3 | E2E・OpenAPI の一致の検査・README などの文書・CI | 済 |
| 6 | 多観点レビュー | 実装を複数の観点で見直す | 済 |
| 7 | 修正 | 6 の指摘を直す | 済 |
| 8 | 事実確認 | README・CLAUDE.md と docs の 3 文書（要件と設計・テスト計画・この文書）の主張を、1 文ずつ根拠（コード・テストの結果・実測）と突き合わせる | 済（2026-10-04・書いた側とは別のエージェントが照合し、× の文は書いた側が直した） |

6・7 は下の [6. 多観点レビューと 7. 修正](#6-多観点レビューと-7-修正2026-10-04) に、8 は [8. 事実確認](#8-事実確認2026-10-04) に書きました。

## 全工程に共通する決まり

指示で決めた条件（実装 3/3 の指示に含まれていたもの。それより前の工程の指示文はこのリポジトリに残っていないので、各工程の記録から分かる範囲です）:

- 題材は社内の備品の貸出管理 API。読み手が短い時間で、業務ロジックの設計・SQL（MyBatis の動的 SQL）・トランザクションと同時実行・テスト・AI での作り方を読み取れることを優先し、作り込み過ぎない。
- 技術: Java 21・Spring Boot 4.1 系・MyBatis（XML のマッパー中心）・PostgreSQL 17・Flyway・Bean Validation・ProblemDetail（RFC 9457）・springdoc-openapi・JUnit 5・AssertJ・Mockito・Testcontainers・Maven Wrapper・GitHub Actions・compose.yaml。認証は範囲外で、`X-User-Id` ヘッダのデモ方式にする。
- 外部から取るのは公式の配布元（start.spring.io・Maven Central・Docker Hub の公式イメージ）だけ。実装 3/3 では、CI で使う actions のバージョンを確かめるため、各 action の公式リポジトリ（GitHub の `actions/checkout`・`actions/setup-java`）のリリース情報も読んだ。
- 実在の会社・人・顧客の情報を入れない。テストのデータは架空（人名は一般的な例、メールは `example.com`）。
- AI で作ったことを README に明記する。記録の無い主張は書かない。性能・件数などの数値は実測した値だけを、実行したコマンドと日付を付けて書く。

テストを緑にする決まり:

- Docker の要らないテスト（単体・Web 層・機械的な検査）は手元で `./mvnw -B -ntp test -DexcludedGroups=docker`。Testcontainers を使うものを含む全部は、Docker の動く Linux マシンの `eclipse-temurin:25-jdk` のコンテナの中で `./mvnw -B -ntp verify` を回す（手元の Docker が動いていないため）。
- 各回の終わりに全テストが緑であることを確かめてから commit する。テストを消したり無効にしたりして緑にしない。
- 設計と違う判断をしたら、同じ回で設計書を直し、変更履歴に残す。
- 同時実行のテストは、ロックや制約を 1 か所ずつ外した写しで赤になることも確かめる（効いていないテストを緑のまま残さない）。

## 1. 要件と設計（2026-10-04）

- 成果: [要件と設計](要件と設計.md) と [テスト計画](テスト計画.md) の v0.1（実装の前）。業務ルール R1〜R9、受け入れ条件（AC）、データモデル、API 一覧、排他制御の方針、採らなかった案、未決事項を先に決めた。
- 受け入れ条件の文は、そのままテストの `@DisplayName` に使う前提で書いた（テスト計画 §6 で AC とテストを対応付ける）。

## 2. 設計レビュー（2026-10-04）

- AI（Claude Code）が設計書を見直し、指摘 21 件を v0.2 に反映した。主な観点は、各文書の変更履歴 v0.2 にあるとおり: 認証の位置（引数のリゾルバだけだと 400 が 401 より先になる → interceptor へ移す）・見せる範囲（借り手の表示名・メールアドレス）・Spring MVC の例外と `code` の対応・problem の `type`・409 と 422 の定義・テストの分け方（コンテナの数・合成注釈・後片付け）・機械的な検査の範囲・用語の統一。
- 反映の後にもう一度見直し、漏れを追記した（`@AppDbTest` に `@AutoConfigureRestTestClient` が要ること、`MutableClock` を `@Primary` で登録すること、`/api/` の下の存在しないパスの 401／404 など）。
- 採らなかった案とその理由は [要件と設計 §13](要件と設計.md#13-採らなかった案と理由) に残した。

## 3. 実装 1/3: 雛形・共通基盤・備品（2026-10-04）

- 雛形: [技術のバージョンの確認](#技術のバージョンの確認) の生成の URL で start.spring.io から作り、`mybatis-spring-boot-starter`（4.1.0）・`mybatis-spring-boot-starter-test`（4.1.0）・`springdoc-openapi-starter-webmvc-ui`（3.1.1）を `pom.xml` に足した。PostgreSQL の JDBC ドライバは、制約名を `PSQLException` から取り出すのでコンパイル時の依存に変えた。
- 作った物: Flyway の V1（3 表・制約・索引）と demo プロファイルだけの初期データ／ProblemDetail の例外ハンドラ・`ErrorCode`・X-User-Id の認証（interceptor と引数リゾルバ）・ページングと並び替えの型・LIKE のエスケープ・制約名の取り出し／備品の登録・検索・詳細・更新・廃棄（コントローラ・サービス・MyBatis の XML）／GitHub Actions の設定。
- テスト: 単体（`EquipmentServiceTest`・`SortParserTest`・`LikePatternsTest`）・機械検査（`MapperXmlSafetyTest`）・Web 層（`EquipmentControllerTest`・`ApiExceptionHandlerTest`）・マッパー（`EquipmentMapperTest`・`SchemaConstraintTest`・`UserMapperTest`。本物の PostgreSQL 17）・文脈の起動（`contextLoads`）。
- 確かめた結果: 2026-10-04、Docker の動く Linux マシンで `eclipse-temurin:25-jdk` のコンテナの中から `./mvnw -B -ntp verify` を実行し、テスト 174 件がすべて成功（失敗・スキップ 0）。
- 実装の途中で分かり、設計書を直したこと（詳細は各文書の変更履歴 v0.3）:
  - Boot 4.1.1 と MyBatis Starter 4.1.0 は組めた（未決事項 Q1 の解決）。
  - MyBatis の `<if test>`（OGNL）で入れ子の record の項目を読む時は `sort.key()` のようにメソッド呼び出しで書く。
  - Jackson 3 は既定で項目名の辞書順に並べるので、ProblemDetail の項目が `detail`・`instance`… の順になっていた。設定で切り、RFC 9457 の例の並び（`type`・`title`・`detail`・`instance`）に合わせて `status` を `title` の後に置いた（実際の応答を出力して気づいた）。
  - 406・415 は 401 より先になると設計に書いていたが、マッピングに `produces`・`consumes` を書いていないので、実際は認証の後に決まる。設計の文を直した（405 は認証より先のまま）。

## 4. 実装 2/3: 貸出（2026-10-04）

- 作った物: 貸出・返却・貸出一覧・貸出の詳細・延滞一覧（`loan` パッケージのコントローラ・サービス・MyBatis の XML）と、利用者行のロック（`UserMapper#lockById`）。
- 進め方: 設計の §4（判定の順番）・§8（ロックの順番）・§9（トランザクションの境界）を、そのまま `LoanService#lend` の文の順番にした。順番は Javadoc に番号付きで書き、単体テストで「複数に当たる時は最初のエラーを返す」「ロックは利用者 → 備品」を確かめる。
- テスト（追加分）:
  - 単体 `LoanServiceTest`（33 件）: 業務ルールの全分岐、判定の順番、ロックの順番、`Clock` を 1 回だけ読むこと、返却の遅れを日本時間の日付で判定すること、一意制約違反を制約名で変換すること。
  - Web 層 `LoanControllerTest`（23 件）。
  - マッパー `LoanMapperTest`（11 件）と `UserMapperTest` の 1 件（本物の PostgreSQL 17）。
  - 同時実行 `ConcurrentLendingTest`（62 件）: CT-1・CT-3・CT-4 を 20 回ずつ、CT-2・CT-5 を 1 回ずつ。最大 8 本（CT-1 は 8 本）のスレッドを `CountDownLatch` でそろえてから一斉に呼び（CT-2 だけはサービスを通らずマッパーの INSERT）、結果の集計を丸ごと比べる。
- 確かめた結果: 2026-10-04、同じ方法で `./mvnw -B -ntp verify` を実行し、テスト 304 件がすべて成功した（失敗・スキップは 0）。
- 排他制御が本当に効いているかの確認: ロックと制約を 1 か所ずつ外した写しで同時実行のテストを流し、狙ったテストが赤になることを確かめた（M1〜M4。結果は [テスト計画 §5](テスト計画.md#5-同時実行テスト)）。本体のコードは書き換えていない。
- 実装の途中で決め、設計書に書き足したこと（要件と設計 v0.4・テスト計画 v0.4）:
  - 応答の `state`（`LENT`/`OVERDUE`/`RETURNED`）と一覧の絞り込みの `state`（`OPEN`/`RETURNED`/`ALL`）は値の種類が違うので、enum を分けた（`LoanState`・`LoanStateFilter`）。
  - 応答の `state` は絞り込みに使わないので、SQL ではなく Java で求めた（備品の貸出状態は絞り込みに使うので SQL の `CASE`）。
  - 未返却の貸出の `returnedLate` は `false` にした（`null` にしない）。

## 5. 実装 3/3: E2E・OpenAPI の一致の検査・文書・CI（2026-10-04）

- 指示の要点: Swagger UI のパスを README に書く・Actuator は health だけを公開・compose.yaml とローカル／テストの設定・GitHub Actions（ubuntu-latest・Java 21・`./mvnw -B verify`）・README（見本であること・主な設計判断を 5 つ前後・API 一覧と curl の例・起動方法・テストの回し方・ディレクトリ構成・AI で作ったことの明記。誇張しない）・この文書と CLAUDE.md・LICENSE（MIT）・.gitignore・.editorconfig。全テストを緑にし、README の curl の例は実際に起動して叩くか、API のテストに同じ要求があることを確かめる。
- 作った物:
  - テスト: `LendingApiEndToEndTest`（テスト計画 §4-4 の手順 1〜9 を、本物の PostgreSQL につないだアプリに HTTP で 1 本通す）・`OpenApiContractTest`（3 件。`/v3/api-docs` の「メソッド＋パス」が設計 §6 の 10 本と一致・全操作に必須の `X-User-Id`・`CurrentUser` が載っていない）・`PublicEndpointsTest`（6 件。Swagger UI が開ける・health が UP・health 以外の Actuator は 404）。
  - 文書: [README](../README.md)・[CLAUDE.md](../CLAUDE.md)・[実行計画の実測](実行計画の実測.md)（データと SQL は `docs/explain/`）・この文書の書き直し・LICENSE・.editorconfig。
  - 設定: GitHub Actions の `actions/checkout` を v4 → v7、`actions/setup-java` を v4 → v6 に上げた（2026-10-04 に GitHub の各リポジトリの最新リリースを確認。setup-java には v4 が非推奨だと警告を出す版 v4.9.1 が出ていた）。checkout は `persist-credentials: false` にし、ジョブに 20 分の上限を付けた。
- 途中で見つけて直したこと:
  - `mvnw` が実行権限の無いファイル（git のモード 100644）として入っていた。GitHub Actions の `./mvnw` が実行できないので、`git update-index --chmod=+x mvnw` で 100755 にした。これまでの確認はリポジトリの写しで毎回 `chmod +x` していたので表に出ていなかった。
  - `compose.yaml` の PostgreSQL がホストの全アドレスで待ち受けていた。手元専用のパスワードなので、`127.0.0.1` だけにした（`spring-boot-docker-compose` が接続先を拾えることは下の起動で確かめた）。
  - 初期データの日付は固定なので、山田さんの返却期限（この時は 2026-10-19。多観点レビューの反映で 2026-10-15 に変えた）より後に動かすと、山田さん・佐藤さんは延滞があって新しく借りられない（422）。README の「借りる」の例がいつでも通るように、貸出を持たない利用者（田中 次郎）を初期データに足し、例の借り手にした。佐藤さんは 2026-10-04 の時点で延滞があり、借りると 422 `BORROWER_HAS_OVERDUE` になることも確かめた。
- 確かめた結果（2026-10-04）:
  - `./mvnw -B -ntp verify`（同じ方法）: 314 件すべて成功（失敗・エラー・スキップ 0）。
  - `./mvnw -B -ntp test -DexcludedGroups=docker`（Windows 11・JDK 21）: 195 件すべて成功。
  - README の curl の例: 下の [README の例の確かめ方](#readme-の例の確かめ方)。
  - `EXPLAIN (ANALYZE, BUFFERS)`: 架空のデータで取り、[実行計画の実測](実行計画の実測.md) に載せた。結果は設計 §10 の見込み（備品検索は索引がほぼ効かない・延滞一覧は部分索引の順に読める）と同じだった。
- 設計書に書き足したこと（要件と設計 v0.5・テスト計画 v0.5）: 実装の規模の実測、`EXPLAIN` の結果への参照、`PublicEndpointsTest`、テストの件数と所要時間の実測、完了の条件の確認結果。

## 6. 多観点レビューと 7. 修正（2026-10-04）

- レビュー: AI（Claude Code）が実装 3/3 の後のリポジトリを、正しさ・セキュリティ・テスト・読み手の観点で見直した。指摘は、別の確認（反証）を通ったものだけを残し、24 件（major 5 件・minor 19 件）になった。多くの指摘は、リポジトリの写しでの再現（使い捨てのテスト・本体を書き換えて赤になるかを見る変異テスト・PostgreSQL での SQL）を根拠に持っていた。
- 修正: 24 件すべてを直した。major は全件、minor も見送ったものは無い。

| 観点 | 指摘 | 直したこと |
|---|---|---|
| 正しさ（major） | 名称の「空白だけは不可」が、全角スペース・NBSP だけの名称を通していた。改行を含む名称には、事実と違う「空白だけ」の文を返していた | 正規表現に `(?U)` を付け、制御文字の判定を別の `@Pattern` と文に分けた（`TextPatterns`。設計 §6） |
| セキュリティ（major） | NUL（U+0000）を含む文字列が検証を通り、PostgreSQL が受け付けずに 500 になっていた（キーワードは MEMBER でも送れる） | 名称・キーワードは制御文字を、備考は改行とタブ以外の制御文字を、400 にした |
| テスト（major） | `ErrorCode` の変換のテストが期待値を enum から作っていて、403・409・422 の取り違えを見逃していた | 設計 §7 の表を書き写した期待値と比べる。Web 層で 403・422 を状態の定数で確かめる |
| テスト（major） | 業務日付を日本時間で求めることを確かめるテストが無かった（UTC と日付がずれる時刻を使っていない） | 2026-10-05 15:30 UTC（日本時間 10-06 0:30）で、期限の範囲・延滞・延滞一覧・検索・詳細・更新を確かめるテストを足した。E2E の手順 6 を日本時間 8:00（UTC ではまだ前日）にした |
| 読み手（major） | README の「PowerShell では `.\mvnw.cmd` に読み替え」のとおりにすると、`-Dspring-boot.run.profiles=demo` が `.` の位置で 2 つに分かれ、起動できなかった | 引数を引用符で囲むコマンドを README に書いた（PowerShell 5.1.26100 で、囲まないと失敗し、囲むと通ることを確かめた） |
| 正しさ | 初期データの山田さんの貸出日時が未来（2026-10-05）で、その前に返却すると CHECK 制約に当たって 500 になる | 2026-10-01（期限 10-15）にした |
| 正しさ | 初期データ（R__）の再実行で、廃棄済みの備品に未返却の貸出が入りうる | `status = 'ACTIVE'` の備品だけに入れる（設計 §11） |
| 正しさ | `Clock` のナノ秒が保存の時にマイクロ秒へ切り上げられ、日付の境目で判定と保存の日付が食い違いうる | `Clock.tick` でマイクロ秒に切り捨てた（設計 §5・`ClockConfigTest`） |
| セキュリティ | `demo` で起動するとホストの全アドレスで待ち受け、同じネットワークの別の端末から ADMIN を名乗れた | `demo` では `server.address=127.0.0.1`（README・設計 §12） |
| セキュリティ | 「すべての失敗応答に `code`」は、Tomcat の段階で弾く要求には当たらない | README と設計の主張を「アプリ（Spring MVC）に届いた要求」に狭め、Tomcat の段階の振る舞いを設計 §7 に書いた |
| テスト | `OpenApiContractTest` が設計書を読まず、テストの中の一覧と比べていた | 設計 §6 の表を読んで比べる |
| テスト | 利用者行が無い時の 401・備考の 500 字・PATCH の名称の 100 字・変更する項目 0 個の `pointer` のテストが無かった | テストを足した |
| 読み手 | DB の作り直し方・CI が流れる条件・`type` の説明・本番の認証に差し替える範囲・LIKE のエスケープの説明・AC の対応表の見出し・変更履歴の並び・EXPLAIN の手順（`createdb` が無い）・テストのログに出る JVM の警告 | README・設計・テスト計画・実行計画の実測・`pom.xml`（テストの JVM の引数）を直した |
| 読み手 | 雛形のコミットのメッセージが、作業の途中の状態を残す自動の commit の文のままだった | push の前に、メッセージを「雛形（start.spring.io で生成し、MyBatis と springdoc を追加）」に書き換えた（中身・日時・作成者は同じ。以後のコミット ID は変わった） |

- テストが効いているかの確認: 直した後、指摘にあった書き換え（業務日付を UTC で求める・利用者行のロックの結果を捨てる・`ErrorCode` の 403 と 422 を別の状態にする）をリポジトリの写しに入れ、Docker の要らないテストを流した。216 件のうち 10 件が赤になった（どれも、この回に足したテストか書き直したテスト）。この写しは確認の後に消し、本体は書き換えていない。
- 確かめた結果（2026-10-04）:
  - `./mvnw -B -ntp verify`（Docker の動く Linux マシンの `eclipse-temurin:25-jdk` のコンテナの中）: 335 件すべて成功（失敗・エラー・スキップ 0）。JVM の警告はログに出なかった。
  - `./mvnw -B -ntp test -DexcludedGroups=docker`（Windows 11・JDK 21）: 216 件すべて成功。
  - README の curl の例: 新しい DB でもう一度流し、期待どおりだった（[README の例の確かめ方](#readme-の例の確かめ方) の 5）。
- 設計書に書き足したこと（要件と設計 v0.6・テスト計画 v0.6）: 上の表の各項目。両方の文書の変更履歴を新しい順に並べ替えた。

## 8. 事実確認（2026-10-04）

- 進め方: README・CLAUDE.md と、docs のうち 3 文書（要件と設計・テスト計画・この文書）から、道具で主張の文を 1 文ずつ表に抜き出した（表はこのリポジトリの外に置いている）。書いた側とは別のエージェント（確認役）が、各文を根拠（コードの行・テストの実行ログ・git の履歴・RFC などの一次資料）と突き合わせ、○（根拠で確かめた）・×・対象外（見出しや指示文など、事実の主張ではないもの）を付けた。× の文は、書いた側が確認役の示した正しい値に合わせて直し、確認役がもう一度確かめる。
- 第 1 周の結果: 表の 327 行（道具が抜き出した 322 文と、手で足した 5 行）のうち、○ 305・対象外 9・× 13。
- × の 13 文と直したこと:

| 種類 | × だった文 | 直したこと |
|---|---|---|
| 言い過ぎ | 同時実行のテストは「8 本のスレッド」をそろえる（README・上の 4.） | 8 本そろうのは CT-1 だけ（CT-3 は 6 本、CT-2・CT-4・CT-5 は 2 本）。CT-2 はサービスを通らずマッパーの INSERT を呼ぶことも書いた |
| 古い実測 | `ConcurrentLendingTest` の所要時間「本体の合計 2.0 秒・クラス全体 5.9 秒」（要件と設計 §16 Q5・テスト計画 §5） | 実装 2/3 の時点（全 304 件）の値だった。テスト計画 §5 を 2026-10-04 の全 335 件の verify の値にし、要件と設計 §16 Q5 は数値を外してテスト計画 §5 を指す形にした |
| 一次資料との食い違い | ProblemDetail の項目の並びは「RFC 9457 の例と同じ type・title・status・detail・instance」、`type` の仮の基底 URI は「RFC 9457 の例と同じ」（要件と設計 §7・§16 Q3） | RFC 9457 の例に `status` は無く、例の `type` は `https://example.com/probs/…`。「例の並び（type・title・detail・instance）に合わせ、status は title の後」「同じなのは example.com のドメイン」に直した。同じ言い方だった上の 3. の文と、`application.properties`・`ErrorCode` のコメントも直した |
| 言い過ぎ | 入力検証の文を自分で書くのは「形の決まった項目（管理番号・名称）」（要件と設計 §7） | 自分で書いているのは `@Pattern` の違反（管理番号の形・空白だけ・制御文字）と `@AssertTrue` の文で、`@NotNull`・`@Size` などは Hibernate Validator の文、と直した |
| 言い過ぎ | README の例を確かめた時に足した環境変数は「〜だけ」（下の [README の例の確かめ方](#readme-の例の確かめ方) の 2・5） | コンテナで Maven を動かすための `HOME`・`MAVEN_USER_HOME` も足していた。「アプリの設定に効くもの」と範囲を書いた |
| 取り違え | メッセージを書き換えたのは「最初のコミット」（上の 6・7 の表） | 書き換えたのは 6 番目の雛形のコミットだった。書き換えた後のメッセージも正しく引いた |
| 言い過ぎ | README の設計の要点は「コードの行へのリンク」（要件と設計 §15） | ファイルだけを指すリンクもあるので「コード（ファイル、または行）へのリンク」と直した |
| 時点のずれ | 事実確認は「これから」「未実施」（要件と設計の冒頭・上の工程の表） | 状態を直し、この節を足した |

- 確かめた結果: コードの変更はコメントの 2 か所（`application.properties`・`ErrorCode`）だけ。直した後、`./mvnw -B -ntp verify`（Docker の動く Linux マシンの `eclipse-temurin:25-jdk` のコンテナの中）で 335 件すべて成功（失敗・エラー・スキップ 0。2026-10-04）。

## README の例の確かめ方

README の [curl の例](../README.md#curl-の例) は、2026-10-04 に次の手順で 1 回ずつ実行し、状態コードと応答を目で確かめた（10 本の API・401・Swagger UI・OpenAPI・health）。

1. Docker の動く Linux マシンで、リポジトリの写しの中から `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` を実行した（`eclipse-temurin:25-jdk` のコンテナの中。ホストの `docker` コマンドと `docker compose` を渡し、`spring-boot-docker-compose` が `compose.yaml` の PostgreSQL を起動した）。
2. 確かめた環境の都合で、アプリの設定に効く環境変数として次を足した（ほかに、コンテナで Maven を動かすための `HOME`・`MAVEN_USER_HOME`）: `SERVER_PORT=18089`・`SERVER_ADDRESS=127.0.0.1`（ほかのサービスと待ち受けがぶつからないように）・`COMPOSE_PROJECT_NAME`（ほかの compose と名前がぶつからないように）・`LOGGING_LEVEL_COM_EXAMPLE_LENDING=DEBUG`（`EXPLAIN` に使う SQL をログから取るため）。
3. README の curl の行をそのまま、`localhost:8080` だけを `127.0.0.1:18089` に置き換えて、上から順に流した。結果は README に書いた期待（201・409・401 など）のとおりだった。README の例を直した後（借り手を田中さんに変えた）、新しい DB でもう一度流し直した。
4. 終わった後、アプリを止め（`compose.yaml` の PostgreSQL も止まったことを確かめた）、コンテナと作ったネットワークを消した。
5. 多観点レビューの指摘を反映した後（初期データの日付・`demo` の待ち受けのアドレスを変えた後）、新しい DB で 3 回目を流した。この回は `SERVER_ADDRESS` を足さず（`application-demo.properties` の `server.address=127.0.0.1` を確かめるため）、アプリの設定に効くものとして足したのは `SERVER_PORT=18089` と `COMPOSE_PROJECT_NAME` だけ（ほかはコンテナで Maven を動かすための `HOME`・`MAVEN_USER_HOME`）。待ち受けが `127.0.0.1:18089` だけであること（`ss -ltn` で確認。別のアドレスからは接続できなかった）と、README の期待どおりの状態コードと応答を確かめた。README の応答の実例 2 つは、この 3 回目の出力を整形したもの。続けて、初期データの山田さんの貸出の返却が 200 になること、NUL を含むキーワードと備考・全角スペースだけの名称が 400 になることも確かめた。終わった後、アプリを止め（PostgreSQL のコンテナも止まった）、`docker compose down -v` でコンテナ・ネットワーク・DB のボリュームを消した。

同じ種類の要求（登録・検索・貸出・二重貸出の 409・延滞一覧・返却・廃棄・401。id や日付は違う）は、`LendingApiEndToEndTest` でもテストのたびに確かめている。

## 人に判断してほしい点（未判断）

この文書を書いた時点で、人が判断したことの記録はこのリポジトリにありません。次の点は AI が決めて先に進めたので、人の判断を待っています。

- 業務ルール・API の形:
  - [ ] `LoanService#lend` の判定の順番とロックの順番が、業務として意図どおりか（要件と設計 §4・§8）。
  - [ ] 未返却の貸出の `returnedLate` を `false` で返す点。
  - [ ] 貸出の時に利用者行が見つからなければ 401 にしている点。利用者を消す API は無いので、起きない想定。
  - [ ] PATCH で変更する項目が 0 個の時の `errors` の `pointer` が `#/anyFieldPresent`（本文に無い名前）になる点を許すか（要件と設計 §6）。
  - [ ] 入力検証の文（Hibernate Validator の日本語の文、例「null は許可されていません」）を、利用者向けにすべて自分の文へ書き換えるか。
  - [ ] 名称・キーワードでは改行とタブを含む制御文字を不可にし、備考では改行とタブだけを許す、という入力の範囲（要件と設計 §6）。
  - [ ] Tomcat の段階で弾く要求（壊れた URL・`TRACE` など）は ProblemDetail にしない点（要件と設計 §7）。
- SQL とテスト:
  - [ ] `mapper/equipment/EquipmentMapper.xml` の検索の SQL（貸出状態の `CASE`・並び替えの `<choose>`・件数の文と一覧の文の条件の共有）が意図どおりに読めるか。
  - [ ] 同時実行のテストの繰り返し 20 回で足りるか。M1 では 20 回中 19 回だけ赤になった。1 回の実行で見逃すことは少ないが、0 ではない。
  - [ ] `EXPLAIN` の架空データの規模（備品 5,000 台・貸出 51,500 件）が、想定の規模の説明として妥当か。
- 公開と運用:
  - [ ] GitHub Actions ではまだ一度も流していない。push の後に緑になるか確かめる。
  - [ ] README のコードへのリンクは、ファイルと行番号への相対リンク（既定のブランチの最新を指す）。特定のコミットに固定した permalink にするなら、push の後に書き換える。
  - [ ] `postgres:17` のタグをマイナーバージョン（例 `17.11`）まで固定するか（要件と設計 §16 Q2）。
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
# GitHub Actions の最新リリース（実装 3/3 で確認）
curl -s https://api.github.com/repos/actions/checkout/releases/latest
curl -s https://api.github.com/repos/actions/setup-java/releases/latest
```

- 生成の URL（mybatis 以外で生成し、`mybatis-spring-boot-starter` と `mybatis-spring-boot-starter-test` の 4.1.0 を `pom.xml` に足す）:
  `https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.1&javaVersion=21&groupId=com.example&artifactId=equipment-lending-api&name=equipment-lending-api&packageName=com.example.lending&dependencies=web,validation,actuator,flyway,postgresql,testcontainers,docker-compose`
- Boot 4 で変わった点（実装時の注意）: スターターが細かく分かれた（例 `spring-boot-starter-webmvc`・`spring-boot-starter-flyway`）、JSON は Jackson 3（3.1.5・パッケージ `tools.jackson`）、テストの Bean 差し替えは `@MockitoBean`、HTTP のテストは `MockMvcTester` と `RestTestClient` が使える。`RestTestClient` の自動設定は `spring-boot-resttestclient`（`spring-boot-starter-webmvc-test` に含まれる）にあり、`@AutoConfigureRestTestClient` を付けた時だけ読まれる（4.1.1 の jar の `META-INF/spring/…AutoConfigureRestTestClient.imports` で確認・`spring.factories` は無い）。
