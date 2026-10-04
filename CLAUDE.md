# このリポジトリで AI（Claude Code）に作業させる時の決まり

社内備品の貸出管理 API の見本です。読み手が短い時間で設計の意図を読み取れることを優先し、機能を増やし過ぎないでください。

## 最初に読む

1. [docs/要件と設計.md](docs/要件と設計.md): 業務ルール（§4）・API（§6）・エラー（§7）・排他制御（§8）・トランザクション（§9）・MyBatis（§10）。
2. [docs/テスト計画.md](docs/テスト計画.md): テストの層・合成注釈（`@MapperDbTest`・`@AppDbTest`）・受け入れ条件とテストの対応。
3. 直すコードと、そのテスト。

設計と違うことをする時は、同じ変更で設計書を直し、変更履歴に 1 行足してください。

## 進め方

1. テストを先に書く。業務ルール 1 つにつき、守れている場合と破った場合の両方。受け入れ条件（AC）に対応するテストは `@DisplayName` に AC の番号を付け、テスト計画 §6 の表も直す。
2. 実装する。
3. 全テストが緑になってから commit する。
   - Docker なし: `./mvnw -B -ntp test -DexcludedGroups=docker`
   - 全部（Docker が要る）: `./mvnw -B -ntp verify`
   - 赤のまま commit しない。テストを消したり `@Disabled` にしたりして緑にしない。
4. README や文書に数値（件数・時間など）を書く時は、実測した値だけを、実行したコマンドと日付を付けて書く。

## コードの決まり

- SQL はすべてマッパー XML（`src/main/resources/mapper/**`）に書く。MyBatis の注釈（`@Select` など）は使わない。
- マッパー XML に `${}` を書かない。値は `#{}` で渡し、並び替えは enum の許可リストを `<choose>` で列に対応させる。
- SQL で `now()`・`CURRENT_DATE` などの時刻関数を使わない。時刻はサービスが `Clock` から 1 操作で 1 回だけ読み、`#{today}` などで渡す。
  - 上の 3 つは `MapperXmlSafetyTest` が検査する。
- `@Transactional` はサービスの公開メソッドだけに付ける。
- ロックの順番は「利用者 → 備品」。ロックを取る操作を足す時は、要件と設計 §8・§9 の表を直し、逆の順番を作らない。
- 一意制約違反は制約名（`DbConstraints`）で見分けて変換する。知らない制約は変換しない。
- 業務エラーは `BusinessException(ErrorCode, detail)` で投げる。新しいエラーは `ErrorCode` に足し、要件と設計 §7 の表も直す。
- API を足す・消す時は、要件と設計 §6 の表と `OpenApiContractTest` の一覧を同じ変更で直す。
- テーブルを変える時は新しい Flyway のマイグレーション（`V2__…`）を足す。`V1` は書き換えない。
- ログに個人情報（メールアドレス・表示名）を出さない。

## 入れてはいけないもの

- 本物の秘密（パスワード・トークン・鍵）。`compose.yaml` の値は手元専用。
- 実在の会社・人・顧客の情報。テストと初期データは架空（人名は「山田 太郎」のような一般的な例、メールは `@example.com`）。
- 記録の無い主張（「本番で使っている」「人がレビューした」など）や、測っていない数値。

## よく使うコマンド

| 目的 | コマンド |
|---|---|
| 1 クラスだけテスト | `./mvnw -B -ntp test -Dtest=LoanServiceTest` |
| Docker なしのテスト | `./mvnw -B -ntp test -DexcludedGroups=docker` |
| 全部のテスト | `./mvnw -B -ntp verify` |
| 起動（架空の初期データつき） | `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` |
