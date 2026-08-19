-- ═══════════════════════════════════════════════════════════════════
-- Phase 7 行政域。版本号区间 V70-V79。schema: oa_admin
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 会议室
CREATE TABLE oa_admin.meeting_room (
    id         bigserial    PRIMARY KEY,
    tenant_id  bigint       NOT NULL DEFAULT 1,
    code       varchar(32)  NOT NULL,
    name       varchar(64)  NOT NULL,
    location   varchar(128),
    capacity   int          NOT NULL DEFAULT 10,
    equipment  varchar(256),
    status     varchar(16)  NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE | DISABLED
    org_id     bigint,
    org_path   varchar(512),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_room_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_room_status CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE INDEX ix_room_org_path ON oa_admin.meeting_room(org_path text_pattern_ops);

-- ★★★ 会议室预定：时间重叠由【数据库】杜绝，不是应用层加锁。
--
-- EXCLUDE USING gist (room_id WITH =, during WITH &&) 的含义是：
-- 不允许存在两行，它们 room_id 相等【且】时间段有交集。
--
-- 为什么不在应用层"先查有没有冲突再插"：那是典型的 check-then-act，
-- 两个并发请求都能查到"没冲突"，然后都插进去。要堵住它得加分布式锁，
-- 而锁的粒度、超时、重入、脑裂全是新的坑。排他约束把这件事下推给数据库，
-- 它在事务隔离级别之下、用索引直接判定，并发再高也只可能成功一条。
--
-- 需要 btree_gist 扩展（V1 baseline 已建）：room_id 是 bigint，
-- 单靠 gist 不支持 = 操作符，必须靠 btree_gist 提供。
CREATE TABLE oa_admin.room_booking (
    id          bigserial   PRIMARY KEY,
    tenant_id   bigint      NOT NULL DEFAULT 1,
    room_id     bigint      NOT NULL REFERENCES oa_admin.meeting_room(id),
    booker_id   varchar(64) NOT NULL,
    booker_name varchar(64),
    subject     varchar(128) NOT NULL,
    attendees   int         NOT NULL DEFAULT 1,
    during      tstzrange   NOT NULL,
    status      varchar(16) NOT NULL DEFAULT 'BOOKED',        -- BOOKED | CANCELLED
    org_id      bigint,
    org_path    varchar(512),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_status CHECK (status IN ('BOOKED','CANCELLED')),
    -- 时间段必须有正长度：起止相同的"零长度预定"能绕过 && 判定，占不住也挡不住任何人
    CONSTRAINT ck_booking_range CHECK (NOT isempty(during)),
    -- 只对 BOOKED 生效：取消掉的预定不该继续占着时间段
    CONSTRAINT ex_room_no_overlap EXCLUDE USING gist (
        room_id WITH =, during WITH &&
    ) WHERE (status = 'BOOKED')
);
CREATE INDEX ix_booking_room_time ON oa_admin.room_booking USING gist (room_id, during);
CREATE INDEX ix_booking_booker ON oa_admin.room_booking(booker_id, created_at DESC);
CREATE INDEX ix_booking_org_path ON oa_admin.room_booking(org_path text_pattern_ops);

-- ───────────────────────────────── 资产
CREATE TABLE oa_admin.asset (
    id           bigserial    PRIMARY KEY,
    tenant_id    bigint       NOT NULL DEFAULT 1,
    asset_no     varchar(32)  NOT NULL,
    name         varchar(128) NOT NULL,
    category     varchar(32)  NOT NULL,
    brand        varchar(64),
    price        numeric(12,2),
    purchased_on date,
    -- IDLE 闲置 / IN_USE 领用中 / REPAIR 维修 / SCRAPPED 报废
    status       varchar(16)  NOT NULL DEFAULT 'IDLE',
    holder_id    varchar(64),
    org_id       bigint,
    org_path     varchar(512),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_asset_no UNIQUE (tenant_id, asset_no),
    CONSTRAINT ck_asset_status CHECK (status IN ('IDLE','IN_USE','REPAIR','SCRAPPED')),
    -- 状态与持有人必须自洽：IN_USE 却没有持有人，是一条永远查不出来的坏账
    CONSTRAINT ck_asset_holder CHECK (
        (status = 'IN_USE' AND holder_id IS NOT NULL) OR (status <> 'IN_USE'))
);
CREATE INDEX ix_asset_org_path ON oa_admin.asset(org_path text_pattern_ops);
CREATE INDEX ix_asset_holder ON oa_admin.asset(holder_id) WHERE holder_id IS NOT NULL;

-- 资产流水：领用/归还/报修/报废。资产当前状态是流水的投影，流水是事实。
CREATE TABLE oa_admin.asset_txn (
    id         bigserial    PRIMARY KEY,
    asset_id   bigint       NOT NULL REFERENCES oa_admin.asset(id),
    action     varchar(16)  NOT NULL,       -- CLAIM | RETURN | REPAIR | SCRAP
    actor_id   varchar(64)  NOT NULL,
    from_status varchar(16),
    to_status   varchar(16) NOT NULL,
    remark     varchar(256),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_asset_action CHECK (action IN ('CLAIM','RETURN','REPAIR','SCRAP'))
);
CREATE INDEX ix_asset_txn_asset ON oa_admin.asset_txn(asset_id, created_at DESC);

-- ───────────────────────────────── 办公用品（有库存，会被抢）
CREATE TABLE oa_admin.supply (
    id         bigserial    PRIMARY KEY,
    tenant_id  bigint       NOT NULL DEFAULT 1,
    code       varchar(32)  NOT NULL,
    name       varchar(64)  NOT NULL,
    unit       varchar(16)  NOT NULL DEFAULT '个',
    stock      int          NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_supply_code UNIQUE (tenant_id, code),
    -- 库存不允许为负。应用层的"先查再扣"在并发下必然超发，
    -- 这条 CHECK 是最后一道 —— 超发时事务直接失败，而不是留下一个负库存。
    CONSTRAINT ck_supply_stock CHECK (stock >= 0)
);

CREATE TABLE oa_admin.supply_request (
    id          bigserial   PRIMARY KEY,
    supply_id   bigint      NOT NULL REFERENCES oa_admin.supply(id),
    requester_id varchar(64) NOT NULL,
    qty         int         NOT NULL,
    org_id      bigint,
    org_path    varchar(512),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_supply_qty CHECK (qty > 0)
);
CREATE INDEX ix_supply_req_org_path ON oa_admin.supply_request(org_path text_pattern_ops);

-- ───────────────────────────────── 车辆（同样是"同一资源不许时间重叠"）
CREATE TABLE oa_admin.vehicle (
    id         bigserial    PRIMARY KEY,
    tenant_id  bigint       NOT NULL DEFAULT 1,
    plate_no   varchar(16)  NOT NULL,
    model      varchar(64),
    seats      int          NOT NULL DEFAULT 5,
    status     varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_vehicle_plate UNIQUE (tenant_id, plate_no)
);

CREATE TABLE oa_admin.vehicle_booking (
    id         bigserial   PRIMARY KEY,
    vehicle_id bigint      NOT NULL REFERENCES oa_admin.vehicle(id),
    booker_id  varchar(64) NOT NULL,
    purpose    varchar(128),
    during     tstzrange   NOT NULL,
    status     varchar(16) NOT NULL DEFAULT 'BOOKED',
    org_id     bigint,
    org_path   varchar(512),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_vehicle_range CHECK (NOT isempty(during)),
    -- 与会议室同构：用车冲突同样交给数据库判，不在应用层再实现一遍
    CONSTRAINT ex_vehicle_no_overlap EXCLUDE USING gist (
        vehicle_id WITH =, during WITH &&
    ) WHERE (status = 'BOOKED')
);
CREATE INDEX ix_vehicle_booking_org_path ON oa_admin.vehicle_booking(org_path text_pattern_ops);

-- ───────────────────────────────── 访客
CREATE TABLE oa_admin.visitor (
    id          bigserial    PRIMARY KEY,
    tenant_id   bigint       NOT NULL DEFAULT 1,
    name        varchar(64)  NOT NULL,
    -- 访客手机号是外部个人信息，密文列 + HMAC 检索列（与 employee 同一套 SensitiveCrypto）
    phone_enc   bytea,
    phone_hash  varchar(64),
    company     varchar(128),
    host_id     varchar(64)  NOT NULL,
    visit_at    timestamptz  NOT NULL,
    leave_at    timestamptz,
    status      varchar(16)  NOT NULL DEFAULT 'BOOKED',   -- BOOKED | CHECKED_IN | LEFT | CANCELLED
    org_id      bigint,
    org_path    varchar(512),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_visitor_status CHECK (status IN ('BOOKED','CHECKED_IN','LEFT','CANCELLED'))
);
CREATE INDEX ix_visitor_host ON oa_admin.visitor(host_id, visit_at DESC);
CREATE INDEX ix_visitor_phone_hash ON oa_admin.visitor(phone_hash);
CREATE INDEX ix_visitor_org_path ON oa_admin.visitor(org_path text_pattern_ops);

-- ───────────────────────────────── 种子
INSERT INTO oa_admin.meeting_room(code, name, location, capacity, equipment) VALUES
  ('R-A101', 'A 座 101 大会议室', 'A 座 1 层', 30, '投影/白板/视频会议'),
  ('R-A201', 'A 座 201 小会议室', 'A 座 2 层', 8,  '白板'),
  ('R-B301', 'B 座 301 培训室',   'B 座 3 层', 60, '投影/音响')
ON CONFLICT DO NOTHING;

INSERT INTO oa_admin.supply(code, name, unit, stock) VALUES
  ('SUP-A4',  'A4 复印纸', '包', 500),
  ('SUP-PEN', '中性笔',    '支', 1000)
ON CONFLICT DO NOTHING;

INSERT INTO oa_admin.vehicle(plate_no, model, seats) VALUES
  ('京A·12345', '别克 GL8', 7),
  ('京A·67890', '丰田凯美瑞', 5)
ON CONFLICT DO NOTHING;
