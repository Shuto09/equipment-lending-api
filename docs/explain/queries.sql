-- アプリが発行した SQL（MyBatis の DEBUG ログの Preparing の文）の ? を、ログの Parameters の値に置き換えたもの。
-- 1 回目は読み込みの温め。2 回目の結果を載せる。
\pset pager off
\echo '=== Q1 備品検索の一覧（keyword=pc, availability=AVAILABLE, sort=name,desc, page=0, size=20）'
SELECT * FROM ( SELECT e.id, e.asset_code, e.name, e.category, e.remarks, e.version, e.created_at, e.disposed_at, l.borrower_id, u.display_name AS borrower_name, l.due_date, CASE WHEN e.status = 'DISPOSED' THEN 'DISPOSED' WHEN l.id IS NULL THEN 'AVAILABLE' WHEN l.due_date < date '2026-10-04' THEN 'OVERDUE' ELSE 'LENT' END AS availability FROM equipment e LEFT JOIN loans l ON l.equipment_id = e.id AND l.returned_at IS NULL LEFT JOIN users u ON u.id = l.borrower_id ) t WHERE (t.name ILIKE '%pc%' ESCAPE '\' OR t.asset_code ILIKE '%pc%' ESCAPE '\') AND t.availability = 'AVAILABLE' ORDER BY t.name DESC NULLS LAST, t.id LIMIT 20 OFFSET 0 \g /dev/null
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM ( SELECT e.id, e.asset_code, e.name, e.category, e.remarks, e.version, e.created_at, e.disposed_at, l.borrower_id, u.display_name AS borrower_name, l.due_date, CASE WHEN e.status = 'DISPOSED' THEN 'DISPOSED' WHEN l.id IS NULL THEN 'AVAILABLE' WHEN l.due_date < date '2026-10-04' THEN 'OVERDUE' ELSE 'LENT' END AS availability FROM equipment e LEFT JOIN loans l ON l.equipment_id = e.id AND l.returned_at IS NULL LEFT JOIN users u ON u.id = l.borrower_id ) t WHERE (t.name ILIKE '%pc%' ESCAPE '\' OR t.asset_code ILIKE '%pc%' ESCAPE '\') AND t.availability = 'AVAILABLE' ORDER BY t.name DESC NULLS LAST, t.id LIMIT 20 OFFSET 0;

\echo '=== Q2 備品検索の件数（同じ条件）'
SELECT count(*) FROM ( SELECT e.id, e.asset_code, e.name, e.category, e.remarks, e.version, e.created_at, e.disposed_at, l.borrower_id, u.display_name AS borrower_name, l.due_date, CASE WHEN e.status = 'DISPOSED' THEN 'DISPOSED' WHEN l.id IS NULL THEN 'AVAILABLE' WHEN l.due_date < date '2026-10-04' THEN 'OVERDUE' ELSE 'LENT' END AS availability FROM equipment e LEFT JOIN loans l ON l.equipment_id = e.id AND l.returned_at IS NULL LEFT JOIN users u ON u.id = l.borrower_id ) t WHERE (t.name ILIKE '%pc%' ESCAPE '\' OR t.asset_code ILIKE '%pc%' ESCAPE '\') AND t.availability = 'AVAILABLE' \g /dev/null
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM ( SELECT e.id, e.asset_code, e.name, e.category, e.remarks, e.version, e.created_at, e.disposed_at, l.borrower_id, u.display_name AS borrower_name, l.due_date, CASE WHEN e.status = 'DISPOSED' THEN 'DISPOSED' WHEN l.id IS NULL THEN 'AVAILABLE' WHEN l.due_date < date '2026-10-04' THEN 'OVERDUE' ELSE 'LENT' END AS availability FROM equipment e LEFT JOIN loans l ON l.equipment_id = e.id AND l.returned_at IS NULL LEFT JOIN users u ON u.id = l.borrower_id ) t WHERE (t.name ILIKE '%pc%' ESCAPE '\' OR t.asset_code ILIKE '%pc%' ESCAPE '\') AND t.availability = 'AVAILABLE';

\echo '=== Q3 延滞一覧（今日 = 2026-10-04, page=0, size=20）'
SELECT l.id, l.equipment_id, e.asset_code, e.name AS equipment_name, l.borrower_id, u.display_name AS borrower_name, u.email AS borrower_email, l.lent_at, l.due_date, CAST(date '2026-10-04' AS date) - l.due_date AS overdue_days FROM loans l JOIN equipment e ON e.id = l.equipment_id JOIN users u ON u.id = l.borrower_id WHERE l.returned_at IS NULL AND l.due_date < date '2026-10-04' ORDER BY l.due_date, l.id LIMIT 20 OFFSET 0 \g /dev/null
EXPLAIN (ANALYZE, BUFFERS)
SELECT l.id, l.equipment_id, e.asset_code, e.name AS equipment_name, l.borrower_id, u.display_name AS borrower_name, u.email AS borrower_email, l.lent_at, l.due_date, CAST(date '2026-10-04' AS date) - l.due_date AS overdue_days FROM loans l JOIN equipment e ON e.id = l.equipment_id JOIN users u ON u.id = l.borrower_id WHERE l.returned_at IS NULL AND l.due_date < date '2026-10-04' ORDER BY l.due_date, l.id LIMIT 20 OFFSET 0;

\echo '=== Q4 延滞一覧の件数'
SELECT count(*) FROM loans l WHERE l.returned_at IS NULL AND l.due_date < date '2026-10-04' \g /dev/null
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM loans l WHERE l.returned_at IS NULL AND l.due_date < date '2026-10-04';
