-- SRM模块数据库初始化脚本
-- 版本：V1.0 日期：2026-09-17
-- 描述：创建SRM模块19张表（8张单头+11张分录）

create schema if not exists srm_schema;

-- ============================================================
-- D1 供应商邀请单
-- ============================================================
create table srm_schema.t_srm_supplier_invite_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    invitee_name varchar(100) not null,
    invitee_credit_code varchar(32),
    invitee_contact varchar(50),
    invitee_phone varchar(20),
    invitee_email varchar(100),
    invited_by varchar(50) not null,
    invite_date date not null default current_date,
    expire_date date not null,
    status varchar(20) not null default 'PENDING',
    related_reg_bill_no varchar(32),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_invite_status check (status in ('PENDING','REGISTERED','DECLINED','EXPIRED'))
);
create index idx_invite_status on srm_schema.t_srm_supplier_invite_h(status);
create index idx_invite_invitee_credit on srm_schema.t_srm_supplier_invite_h(invitee_credit_code) where invitee_credit_code is not null;

create table srm_schema.t_srm_supplier_invite_e_cat (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_invite_h(id),
    seq int not null,
    category_code varchar(32) not null,
    category_name varchar(100) not null,
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_invite_cat_header on srm_schema.t_srm_supplier_invite_e_cat(header_id);

-- ============================================================
-- D2 供应商注册申请单
-- ============================================================
create table srm_schema.t_srm_supplier_reg_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    supplier_name varchar(100) not null,
    unified_credit_code varchar(32),
    contact_name varchar(50),
    contact_phone varchar(20),
    contact_email varchar(100),
    reg_address varchar(200),
    cooperation_note text,
    apply_date date not null default current_date,
    source_type varchar(20) not null default 'SELF_REG',
    source_invite_bill_no varchar(32),
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_reg_source_type check (source_type in ('SELF_REG','INVITED')),
    constraint ck_reg_status check (status in ('DRAFT','SUBMITTED','IN_CERT','CONVERTED'))
);
create index idx_reg_status on srm_schema.t_srm_supplier_reg_h(status);
create index idx_reg_credit_code on srm_schema.t_srm_supplier_reg_h(unified_credit_code) where unified_credit_code is not null;

create table srm_schema.t_srm_supplier_reg_e_cat (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_reg_h(id),
    seq int not null,
    category_code varchar(32) not null,
    category_name varchar(100) not null,
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_reg_cat_header on srm_schema.t_srm_supplier_reg_e_cat(header_id);

create table srm_schema.t_srm_supplier_reg_e_cert (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_reg_h(id),
    seq int not null,
    cert_type varchar(20) not null,
    cert_no varchar(50),
    issue_org varchar(100),
    valid_from date,
    valid_to date,
    attachment_url varchar(300),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_reg_cert_type check (cert_type in ('BUSINESS_LICENSE','QUALIFICATION','ISO'))
);
create index idx_reg_cert_header on srm_schema.t_srm_supplier_reg_e_cert(header_id);

-- ============================================================
-- D3 供应商认证单
-- ============================================================
create table srm_schema.t_srm_supplier_cert_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    source_bill_no varchar(32),
    source_bill_type varchar(20),
    supplier_name varchar(100) not null,
    unified_credit_code varchar(32),
    cert_type varchar(20) not null default 'FIRST',
    start_date date not null default current_date,
    responsible_person varchar(50) not null,
    conclusion varchar(20),
    valid_until date,
    status varchar(20) not null default 'DRAFT',
    approved_at timestamptz,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_cert_type check (cert_type in ('FIRST','RENEWAL')),
    constraint ck_cert_conclusion check (conclusion in ('QUALIFIED','TEMP_QUALIFIED','UNQUALIFIED')),
    constraint ck_cert_status check (status in ('DRAFT','IN_REVIEW','APPROVED','REJECTED'))
);
create index idx_cert_status on srm_schema.t_srm_supplier_cert_h(status);
create index idx_cert_supplier_name on srm_schema.t_srm_supplier_cert_h(supplier_name);

create table srm_schema.t_srm_supplier_cert_e (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_cert_h(id),
    seq int not null,
    dimension varchar(20) not null,
    result varchar(20) not null default 'PENDING',
    score decimal(5,2),
    responsible_person varchar(50),
    remark varchar(200),
    attachment_url varchar(300),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_cert_dim check (dimension in ('QUALIFICATION','ISO','SAMPLE','ONSITE')),
    constraint ck_cert_result check (result in ('PASS','FAIL','PENDING'))
);
create index idx_cert_e_header on srm_schema.t_srm_supplier_cert_e(header_id);

-- ============================================================
-- D4 供应商档案（主档案）
-- ============================================================
create table srm_schema.t_srm_supplier_archive_h (
    id bigserial primary key,
    supplier_code varchar(32) not null unique,
    supplier_name varchar(100) not null,
    unified_credit_code varchar(32),
    supplier_type varchar(20),
    cert_status varchar(20) not null default 'POTENTIAL',
    cert_valid_until date,
    perf_grade varchar(5),
    perf_eval_count int not null default 0,
    last_perf_date date,
    bank_name varchar(100),
    bank_account varchar(50),
    bank_account_name varchar(100),
    source_cert_bill_no varchar(32),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_archive_supplier_type check (supplier_type in ('MANUFACTURER','DEALER','SERVICE')),
    constraint ck_archive_cert_status check (cert_status in ('POTENTIAL','TEMP_QUALIFIED','QUALIFIED','FROZEN','BLACKLIST')),
    constraint ck_archive_perf_grade check (perf_grade in ('A','B','C'))
);
create unique index idx_archive_credit_code on srm_schema.t_srm_supplier_archive_h(unified_credit_code) where unified_credit_code is not null;

create table srm_schema.t_srm_supplier_archive_e_cat (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_archive_h(id),
    seq int not null,
    category_code varchar(32) not null,
    category_name varchar(100) not null,
    capability_level varchar(10),
    region_coverage varchar(100),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_cat_capability check (capability_level in ('HIGH','MID','LOW'))
);
create index idx_archive_cat_header on srm_schema.t_srm_supplier_archive_e_cat(header_id);

create table srm_schema.t_srm_supplier_archive_e_cert (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_archive_h(id),
    seq int not null,
    cert_type varchar(20) not null,
    cert_no varchar(50),
    issue_org varchar(100),
    valid_from date,
    valid_to date,
    attachment_url varchar(300),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_archive_cert_header on srm_schema.t_srm_supplier_archive_e_cert(header_id);
create index idx_archive_cert_valid_to on srm_schema.t_srm_supplier_archive_e_cert(valid_to) where valid_to is not null;

create table srm_schema.t_srm_supplier_archive_e_contact (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_archive_h(id),
    seq int not null,
    contact_name varchar(50) not null,
    position varchar(50),
    phone varchar(20),
    email varchar(100),
    is_primary boolean not null default false,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_archive_contact_header on srm_schema.t_srm_supplier_archive_e_contact(header_id);

-- ============================================================
-- D5 供应商变更单
-- ============================================================
create table srm_schema.t_srm_supplier_change_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    supplier_code varchar(32) not null,
    change_type varchar(20) not null,
    change_reason varchar(200),
    applicant varchar(50) not null,
    apply_date date not null default current_date,
    status varchar(20) not null default 'DRAFT',
    approved_at timestamptz,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_change_type check (change_type in ('BASIC','CONTACT','BANK','CERT','CATEGORY')),
    constraint ck_change_status check (status in ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED'))
);
create index idx_change_status on srm_schema.t_srm_supplier_change_h(status);
create index idx_change_supplier_code on srm_schema.t_srm_supplier_change_h(supplier_code);

create table srm_schema.t_srm_supplier_change_e (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_change_h(id),
    seq int not null,
    field_name varchar(50) not null,
    old_value varchar(200),
    new_value varchar(200) not null,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_change_e_header on srm_schema.t_srm_supplier_change_e(header_id);

-- ============================================================
-- D6 供应商冻结/解冻申请单
-- ============================================================
create table srm_schema.t_srm_supplier_freeze_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    supplier_code varchar(32) not null,
    apply_type varchar(10) not null,
    trigger_source varchar(20) not null default 'MANUAL',
    source_perf_bill_no varchar(32),
    reason text not null,
    applicant varchar(50) not null,
    apply_date date not null default current_date,
    status varchar(20) not null default 'DRAFT',
    effective_date date,
    pre_status_snapshot varchar(20),
    rectification_note text,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_freeze_type check (apply_type in ('FREEZE','UNFREEZE')),
    constraint ck_freeze_trigger check (trigger_source in ('PERF_WARNING','COMPLIANCE','MANUAL','SYSTEM_AUTO')),
    constraint ck_freeze_status check (status in ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED'))
);
create index idx_freeze_status on srm_schema.t_srm_supplier_freeze_h(status);
create index idx_freeze_supplier_code on srm_schema.t_srm_supplier_freeze_h(supplier_code);

create table srm_schema.t_srm_supplier_freeze_e (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_freeze_h(id),
    seq int not null,
    scope_type varchar(10) not null default 'ALL',
    category_code varchar(32),
    category_name varchar(100),
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_freeze_scope check (scope_type in ('ALL','CATEGORY'))
);
create index idx_freeze_e_header on srm_schema.t_srm_supplier_freeze_e(header_id);

-- ============================================================
-- D7 供应商黑名单单
-- ============================================================
create table srm_schema.t_srm_supplier_blacklist_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    supplier_code varchar(32) not null,
    trigger_source varchar(20) not null default 'MANUAL',
    source_perf_bill_no varchar(32),
    reason text not null,
    applicant varchar(50) not null,
    apply_date date not null default current_date,
    approval_level varchar(20),
    is_permanent boolean not null default true,
    review_date date,
    status varchar(20) not null default 'DRAFT',
    effective_date date,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_blacklist_trigger check (trigger_source in ('PERF_SEVERE','COMPLIANCE_VIOLATION','FRAUD','MANUAL')),
    constraint ck_blacklist_status check (status in ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED'))
);
create index idx_blacklist_status on srm_schema.t_srm_supplier_blacklist_h(status);
create index idx_blacklist_supplier_code on srm_schema.t_srm_supplier_blacklist_h(supplier_code);

create table srm_schema.t_srm_supplier_blacklist_e (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_blacklist_h(id),
    seq int not null,
    evidence_type varchar(30),
    evidence_desc varchar(300),
    attachment_url varchar(300),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_blacklist_e_header on srm_schema.t_srm_supplier_blacklist_e(header_id);

-- ============================================================
-- D8 供应商绩效考核单
-- ============================================================
create table srm_schema.t_srm_supplier_perf_h (
    id bigserial primary key,
    bill_no varchar(32) not null unique,
    supplier_code varchar(32) not null,
    period varchar(20) not null,
    eval_type varchar(10) not null default 'QUARTERLY',
    total_score decimal(5,2),
    grade varchar(5),
    status varchar(20) not null default 'DRAFT',
    evaluator varchar(50) not null,
    eval_date date not null default current_date,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_perf_type check (eval_type in ('MONTHLY','QUARTERLY','ANNUAL')),
    constraint ck_perf_grade check (grade in ('A','B','C')),
    constraint ck_perf_status check (status in ('DRAFT','SUBMITTED','CONFIRMED'))
);
create index idx_perf_status on srm_schema.t_srm_supplier_perf_h(status);
create index idx_perf_supplier_code on srm_schema.t_srm_supplier_perf_h(supplier_code);
create index idx_perf_period on srm_schema.t_srm_supplier_perf_h(period);

create table srm_schema.t_srm_supplier_perf_e (
    id bigserial primary key,
    header_id bigint not null references srm_schema.t_srm_supplier_perf_h(id),
    seq int not null,
    dimension varchar(10) not null,
    weight decimal(5,2) not null,
    score decimal(5,2) not null,
    source_bill_no varchar(32),
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_perf_dim check (dimension in ('QUALITY','COST','DELIVERY','SERVICE'))
);
create index idx_perf_e_header on srm_schema.t_srm_supplier_perf_e(header_id);

-- ============================================================
-- Outbox事件表
-- ============================================================
create table srm_schema.outbox_event (
    event_id uuid primary key,
    aggregate_type varchar(64) not null,
    aggregate_id varchar(64) not null,
    event_type varchar(128) not null,
    event_version varchar(16) not null,
    payload jsonb not null,
    routing_key varchar(160) not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    retry_count integer not null default 0,
    next_retry_at timestamptz not null
);
create index idx_outbox_pending on srm_schema.outbox_event(next_retry_at, occurred_at) where published_at is null;
