-- STC模块数据库初始化脚本
-- 版本：V1.0 日期：2026-10-14
-- 描述：创建STC模块18张表（8张单头+10张分录）

create schema if not exists stc_schema;

-- ============================================================
-- D9 寻源立项单
-- ============================================================
create table stc_schema.t_stc_sourcing_project_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    project_name varchar(200) not null,
    category_code varchar(32) not null,
    category_name varchar(100) not null,
    budget_amount decimal(19,2) not null,
    currency varchar(10) not null default 'CNY',
    plan_start_date date not null,
    plan_end_date date not null,
    sourcing_method varchar(20) not null,
    single_source_reason varchar(500),
    initiator varchar(50) not null,
    apply_date date not null default current_date,
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_project_method check (sourcing_method in ('TENDER','RFQ','NEGOTIATION','SINGLE_SOURCE')),
    constraint ck_project_status check (status in ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','UNAPPROVED','CLOSED','CANCELLED'))
);
create index idx_project_status on stc_schema.t_stc_sourcing_project_h(status);

create table stc_schema.t_stc_sourcing_project_e_cand (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_sourcing_project_h(id),
    seq int not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    match_result varchar(50),
    cert_status varchar(20),
    perf_grade varchar(5),
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_project_cand_header on stc_schema.t_stc_sourcing_project_e_cand(header_id);

-- ============================================================
-- D10 招标单（开评标）
-- ============================================================
create table stc_schema.t_stc_tender_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    source_project_bill_no varchar(40),
    tender_title varchar(200) not null,
    tender_type varchar(20) not null,
    announce_date date not null,
    bid_deadline date not null,
    open_date date not null,
    need_prequalification boolean not null default false,
    evaluation_method varchar(20) not null,
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_tender_type check (tender_type in ('PUBLIC','INVITED')),
    constraint ck_tender_eval_method check (evaluation_method in ('LOWEST_PRICE','COMPREHENSIVE')),
    constraint ck_tender_status check (status in ('DRAFT','ANNOUNCED','BIDDING','OPENED','EVALUATING','AWARDED','CANCELLED'))
);
create index idx_tender_status on stc_schema.t_stc_tender_h(status);

create table stc_schema.t_stc_tender_e_bidder (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_tender_h(id),
    seq int not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    bid_amount decimal(19,2),
    submit_time timestamptz,
    prequal_result varchar(10) not null default 'NA',
    is_shortlisted boolean not null default false,
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_bidder_prequal check (prequal_result in ('PASS','FAIL','NA'))
);
create index idx_tender_bidder_header on stc_schema.t_stc_tender_e_bidder(header_id);

create table stc_schema.t_stc_tender_e_eval (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_tender_h(id),
    seq int not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    tech_score decimal(5,2),
    biz_score decimal(5,2),
    total_score decimal(6,2),
    rank int,
    judge_comment varchar(300),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_tender_eval_header on stc_schema.t_stc_tender_e_eval(header_id);

-- ============================================================
-- D11 询价单（多轮比价）
-- ============================================================
create table stc_schema.t_stc_rfq_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    source_project_bill_no varchar(40),
    rfq_title varchar(200) not null,
    max_rounds int not null default 3,
    current_round int not null default 1,
    quote_deadline date not null,
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_rfq_status check (status in ('DRAFT','PUBLISHED','QUOTING','COMPARING','CLOSED'))
);
create index idx_rfq_status on stc_schema.t_stc_rfq_h(status);

create table stc_schema.t_stc_rfq_e_quote (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_rfq_h(id),
    round_no int not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    item_code varchar(32) not null,
    item_name varchar(100) not null,
    qty decimal(19,4) not null,
    unit varchar(16) not null,
    quote_unit_price decimal(19,4) not null,
    quote_amount decimal(19,2) not null,
    quote_date date not null,
    delivery_days int,
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_rfq_quote_header on stc_schema.t_stc_rfq_e_quote(header_id);

-- ============================================================
-- D12 定标单（C3 风控守门）
-- ============================================================
create table stc_schema.t_stc_award_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    source_bill_type varchar(20) not null,
    source_bill_no varchar(40) not null,
    award_supplier_code varchar(32) not null,
    award_supplier_name varchar(100) not null,
    award_conclusion varchar(500) not null,
    total_award_amount decimal(19,2) not null,
    currency varchar(10) not null default 'CNY',
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_award_source_type check (source_bill_type in ('TENDER','RFQ','SINGLE_SOURCE')),
    constraint ck_award_status check (status in ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','UNAPPROVED','CLOSED','CANCELLED'))
);
create index idx_award_status on stc_schema.t_stc_award_h(status);
create index idx_award_supplier_code on stc_schema.t_stc_award_h(award_supplier_code);

create table stc_schema.t_stc_award_e_item (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_award_h(id),
    seq int not null,
    category_code varchar(32),
    item_code varchar(32) not null,
    item_name varchar(100) not null,
    spec varchar(200),
    qty decimal(19,4) not null,
    unit varchar(16) not null,
    unit_price decimal(19,4) not null,
    total_amount decimal(19,2) not null,
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_award_item_header on stc_schema.t_stc_award_e_item(header_id);

-- ============================================================
-- D13 采购合同
-- ============================================================
create table stc_schema.t_stc_contract_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    source_award_bill_no varchar(40),
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    contract_name varchar(200) not null,
    contract_amount decimal(19,2) not null,
    currency varchar(10) not null default 'CNY',
    valid_from date not null,
    valid_to date not null,
    contract_type varchar(20) not null,
    signatory varchar(50) not null,
    legal_review_status varchar(20) not null default 'PENDING',
    seal_status varchar(20) not null default 'UNSEALED',
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_contract_type check (contract_type in ('FRAMEWORK','ONE_TIME')),
    constraint ck_contract_legal check (legal_review_status in ('APPROVED','IN_REVIEW','PENDING')),
    constraint ck_contract_seal check (seal_status in ('SEALED','UNSEALED')),
    constraint ck_contract_status check (status in ('DRAFT','IN_REVIEW','APPROVED','EXPIRED','TERMINATED'))
);
create index idx_contract_status on stc_schema.t_stc_contract_h(status);

create table stc_schema.t_stc_contract_e_clause (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_contract_h(id),
    seq int not null,
    clause_type varchar(20) not null,
    category_code varchar(32),
    item_name varchar(100),
    qty decimal(19,4),
    unit_price decimal(19,4),
    remark varchar(300),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_clause_type check (clause_type in ('TARGET','PAYMENT','WARRANTY','OTHER'))
);
create index idx_contract_clause_header on stc_schema.t_stc_contract_e_clause(header_id);

-- ============================================================
-- D14 基准价目表
-- ============================================================
create table stc_schema.t_stc_price_list_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    source_award_bill_no varchar(40),
    price_list_name varchar(200) not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    effective_from date not null,
    effective_to date not null,
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_price_list_status check (status in ('DRAFT','EFFECTIVE','EXPIRED','SUPERSEDED'))
);
create index idx_price_list_status on stc_schema.t_stc_price_list_h(status);

create table stc_schema.t_stc_price_list_e_item (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_price_list_h(id),
    seq int not null,
    category_code varchar(32) not null,
    item_code varchar(32) not null,
    item_name varchar(100) not null,
    unit_price decimal(19,4) not null,
    currency varchar(10) not null default 'CNY',
    tiered_price_note varchar(200),
    regional_diff_note varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_price_list_item_header on stc_schema.t_stc_price_list_e_item(header_id);

-- ============================================================
-- D15 框架协议（BPA）
-- ============================================================
create table stc_schema.t_stc_bpa_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    source_award_bill_no varchar(40),
    agreement_name varchar(200) not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    total_quota_amount decimal(19,2) not null,
    used_quota_amount decimal(19,2) not null default 0,
    currency varchar(10) not null default 'CNY',
    valid_from date not null,
    valid_to date not null,
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_bpa_status check (status in ('DRAFT','EFFECTIVE','EXHAUSTED','EXPIRED','TERMINATED'))
);
create index idx_bpa_status on stc_schema.t_stc_bpa_h(status);

create table stc_schema.t_stc_bpa_e_cat (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_bpa_h(id),
    seq int not null,
    category_code varchar(32) not null,
    category_name varchar(100) not null,
    item_code varchar(32),
    unit_price decimal(19,4),
    max_qty decimal(19,4),
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_bpa_cat_header on stc_schema.t_stc_bpa_e_cat(header_id);

-- ============================================================
-- D16 SR 配额单
-- ============================================================
create table stc_schema.t_stc_supplier_ratio_h (
    id bigserial primary key,
    bill_no varchar(40) not null unique,
    category_code varchar(32) not null,
    category_name varchar(100) not null,
    ratio_cycle varchar(20) not null,
    status varchar(20) not null default 'DRAFT',
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now(),
    constraint ck_ratio_status check (status in ('DRAFT','EFFECTIVE','EXPIRED'))
);
create index idx_ratio_status on stc_schema.t_stc_supplier_ratio_h(status);

create table stc_schema.t_stc_supplier_ratio_e (
    id bigserial primary key,
    header_id bigint not null references stc_schema.t_stc_supplier_ratio_h(id),
    seq int not null,
    supplier_code varchar(32) not null,
    supplier_name varchar(100) not null,
    ratio_percent decimal(5,2) not null,
    remark varchar(200),
    created_by varchar(32),
    created_at timestamptz not null default now(),
    updated_by varchar(32),
    updated_at timestamptz not null default now()
);
create index idx_ratio_e_header on stc_schema.t_stc_supplier_ratio_e(header_id);

