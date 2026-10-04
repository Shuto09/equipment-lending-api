-- 手元で動かすときの架空の初期データ（demo プロファイルだけで読む。テストでは読まない）
-- Flyway の繰り返し実行のマイグレーション（中身が変わるたびに再実行される）なので、何度流しても同じ結果になるように書く。
-- 人名・メールアドレス・管理番号はすべて架空。
-- 新しい DB に流した場合の利用者の id: 1 = 鈴木 一郎（ADMIN）・2 = 山田 太郎・3 = 佐藤 花子・4 = 田中 次郎（X-User-Id に使う）。
-- 日付は固定（時刻を決めるのはアプリの Clock だけ、という方針に合わせる）。動かす日によっては貸出が延滞に見える。
-- 貸出の日時は README の例を確かめた日（2026-10-04）より前にする（未来の貸出日時だと、それより前の返却が
-- CHECK ck_loans_returned_after_lent に当たって 500 になる）。
-- 田中さんには貸出を持たせない（いつ動かしても延滞が無く、借りる操作を試せるように）。

INSERT INTO users (display_name, email, role) VALUES
    ('鈴木 一郎', 'suzuki@example.com', 'ADMIN'),
    ('山田 太郎', 'yamada@example.com', 'MEMBER'),
    ('佐藤 花子', 'sato@example.com',   'MEMBER'),
    ('田中 次郎', 'tanaka@example.com', 'MEMBER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO equipment (asset_code, name, category, remarks, created_at) VALUES
    ('PC-0001',  'ノート PC 14 インチ',          'LAPTOP',    NULL,                  '2026-09-01 09:00:00+09'),
    ('PC-0002',  'ノート PC 14 インチ',          'LAPTOP',    NULL,                  '2026-09-01 09:00:00+09'),
    ('PC-0003',  'ノート PC 16 インチ',          'LAPTOP',    '充電器は別の袋',       '2026-09-01 09:00:00+09'),
    ('MON-0001', 'モニター 27 インチ',           'MONITOR',   NULL,                  '2026-09-02 09:00:00+09'),
    ('MON-0002', 'モニター 24 インチ',           'MONITOR',   'HDMI ケーブル付き',    '2026-09-02 09:00:00+09'),
    ('PJ-0001',  'プロジェクター（会議室用）',     'PROJECTOR', NULL,                  '2026-09-03 09:00:00+09'),
    ('OTH-0001', 'USB-C 充電器 100W',           'OTHER',     NULL,                  '2026-09-03 09:00:00+09')
ON CONFLICT (asset_code) DO NOTHING;

-- 廃棄済みの備品（検索の既定の結果には出ない）
INSERT INTO equipment (asset_code, name, category, status, disposed_at, created_at) VALUES
    ('MON-0003', 'モニター 21 インチ（旧型）', 'MONITOR', 'DISPOSED', '2026-09-30 17:00:00+09', '2026-09-02 09:00:00+09')
ON CONFLICT (asset_code) DO NOTHING;

-- 未返却の貸出だけを入れる（返却済みの行は重複を判定する一意の手がかりが無く、再実行のたびに増えるため）。
-- id は固定できないので、利用者と備品は自然キー（メールアドレス・管理番号）で引く。
-- この SQL はアプリの判定を通らない書き込みなので、廃棄済みの備品（再実行の時点で廃棄されたものを含む）には入れない（R2）。
INSERT INTO loans (equipment_id, borrower_id, lent_at, due_date)
SELECT e.id, u.id, v.lent_at, v.due_date
  FROM (VALUES
          ('PC-0002', 'yamada@example.com', timestamptz '2026-10-01 10:00:00+09', date '2026-10-15'),
          ('PJ-0001', 'sato@example.com',   timestamptz '2026-09-25 13:00:00+09', date '2026-10-02')
       ) AS v (asset_code, email, lent_at, due_date)
  JOIN equipment e ON e.asset_code = v.asset_code AND e.status = 'ACTIVE'
  JOIN users u ON u.email = v.email
ON CONFLICT (equipment_id) WHERE returned_at IS NULL DO NOTHING;
