-- EXPLAIN 用の架空データ（V1 を当てた空の DB に流す）。人名・メールはすべて架空。
-- 利用者 1,000 人・備品 5,000 台（うち廃棄済み 500）・返却済みの貸出 50,000 件・未返却の貸出 1,500 件。
INSERT INTO users (display_name, email, role)
SELECT '利用者 ' || lpad(i::text, 4, '0'), 'user' || lpad(i::text, 4, '0') || '@example.com',
       CASE WHEN i <= 10 THEN 'ADMIN' ELSE 'MEMBER' END
  FROM generate_series(1, 1000) AS i;

INSERT INTO equipment (asset_code, name, category, status, disposed_at, created_at)
SELECT c.prefix || '-' || lpad(n::text, 4, '0'),
       c.label || ' ' || (n % 7 + 1) || ' 型',
       c.category,
       CASE WHEN n % 10 = 0 THEN 'DISPOSED' ELSE 'ACTIVE' END,
       CASE WHEN n % 10 = 0 THEN timestamptz '2026-09-30 17:00:00+09' END,
       timestamptz '2025-04-01 09:00:00+09' + (n || ' hours')::interval
  FROM (VALUES ('PC',  'ノート PC',      'LAPTOP',    2000),
               ('MON', 'モニター',        'MONITOR',   1500),
               ('PJ',  'プロジェクター',  'PROJECTOR',  300),
               ('OTH', '周辺機器',        'OTHER',     1200)) AS c (prefix, label, category, total)
 CROSS JOIN LATERAL generate_series(1, c.total) AS n;

-- 返却済み: 1 台あたり 10 件（廃棄済みの備品も含む）。2025-10 から 2026-09 に分散
INSERT INTO loans (equipment_id, borrower_id, lent_at, due_date, returned_at)
SELECT e.id,
       (e.id * 7 + k * 13) % 1000 + 1,
       timestamptz '2025-10-01 10:00:00+09' + ((e.id + k * 31) % 360 || ' days')::interval,
       (date '2025-10-01' + ((e.id + k * 31) % 360 + 7)::int),
       timestamptz '2025-10-01 10:00:00+09' + ((e.id + k * 31) % 360 + 5 || ' days')::interval
  FROM equipment e
 CROSS JOIN generate_series(1, 10) AS k;

-- 未返却: 廃棄済みでない備品のうち 1,500 台。返却期限は 2026-09-20〜2026-10-18 に分散（2026-10-04 時点で一部が延滞）
INSERT INTO loans (equipment_id, borrower_id, lent_at, due_date)
SELECT e.id,
       (row_number() OVER (ORDER BY e.id) - 1) % 990 + 11,
       timestamptz '2026-09-15 10:00:00+09',
       date '2026-09-20' + ((row_number() OVER (ORDER BY e.id)) % 29)::int
  FROM equipment e
 WHERE e.status = 'ACTIVE'
 ORDER BY e.id
 LIMIT 1500;

ANALYZE;

SELECT (SELECT count(*) FROM users) AS users,
       (SELECT count(*) FROM equipment) AS equipment,
       (SELECT count(*) FROM equipment WHERE status = 'DISPOSED') AS disposed,
       (SELECT count(*) FROM loans WHERE returned_at IS NOT NULL) AS returned_loans,
       (SELECT count(*) FROM loans WHERE returned_at IS NULL) AS open_loans,
       (SELECT count(*) FROM loans WHERE returned_at IS NULL AND due_date < date '2026-10-04') AS overdue_loans;
