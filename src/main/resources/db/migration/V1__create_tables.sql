-- 社内備品の貸出管理: テーブル・制約・索引（docs/要件と設計.md §5）
-- 制約名は Java の DbConstraints と一致させる（一意制約違反を「どの制約か」で 409 に変換するのに使う）。
-- 時刻はすべてアプリの Clock から渡すので、DEFAULT now() などは書かない。

CREATE TABLE users (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    display_name varchar(50)  NOT NULL,
    email        varchar(254) NOT NULL,
    role         varchar(10)  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('MEMBER', 'ADMIN'))
);

CREATE TABLE equipment (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_code  varchar(20)  NOT NULL,
    name        varchar(100) NOT NULL,
    category    varchar(20)  NOT NULL,
    remarks     varchar(500),
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    disposed_at timestamptz,
    version     integer      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT uq_equipment_asset_code UNIQUE (asset_code),
    CONSTRAINT ck_equipment_category CHECK (category IN ('LAPTOP', 'MONITOR', 'PROJECTOR', 'OTHER')),
    CONSTRAINT ck_equipment_status   CHECK (status IN ('ACTIVE', 'DISPOSED')),
    -- 廃棄済みのときだけ廃棄日時がある
    CONSTRAINT ck_equipment_disposed CHECK ((status = 'DISPOSED') = (disposed_at IS NOT NULL)),
    -- 備考の「空」は NULL の 1 種類だけにする（空文字はアプリで NULL にそろえてから保存する）
    CONSTRAINT ck_equipment_remarks_not_empty CHECK (remarks <> '')
);

CREATE TABLE loans (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    equipment_id bigint      NOT NULL REFERENCES equipment (id),
    borrower_id  bigint      NOT NULL REFERENCES users (id),
    lent_at      timestamptz NOT NULL,
    due_date     date        NOT NULL,
    returned_at  timestamptz,
    CONSTRAINT ck_loans_returned_after_lent CHECK (returned_at IS NULL OR returned_at >= lent_at)
);

-- 二重貸出の禁止（R1）: 未返却の行だけを対象にした一意インデックス。
-- アプリの判定をすり抜けた経路（別のアプリ・手作業の SQL・将来のバグ）でも、1 台に未返却 2 件は入らない。
CREATE UNIQUE INDEX uq_loans_open_equipment ON loans (equipment_id) WHERE returned_at IS NULL;

-- 延滞一覧（R9）: 未返却の行だけを、一覧の並び順（期限・id）と同じ順で持つ
CREATE INDEX ix_loans_open_due_date ON loans (due_date, id) WHERE returned_at IS NULL;

-- 外部キー側の索引（PostgreSQL は自動では作らない）
CREATE INDEX ix_loans_equipment ON loans (equipment_id);
CREATE INDEX ix_loans_borrower  ON loans (borrower_id);
