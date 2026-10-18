-- ============================================================
-- 核心业务单据与主数据 Schema V3
-- 覆盖 SRM/STC/PTP 三大模块的所有核心单据与主数据
-- ============================================================

create schema if not exists master_schema;
create schema if not exists procurement_schema;
create schema if not exists sourcing_schema;
create schema if not exists settlement_schema;
create schema if not exists workflow_schema;

-- ============================================================
-- 主数据：物料 master_schema.material
-- ============================================================
create table master_schema.material (
  id uuid primary key,
  material_no varchar(32) not null unique,
  name varchar(200) not null,
  specification varchar(500),
  category varchar(64) not null,
  unit varchar(16) not null,
  reference_price numeric(19,4),
  is_active boolean not null default true,
  description varchar(1000),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_material_category on master_schema.material(category);
create index idx_material_active on master_schema.material(is_active);

-- ============================================================
-- 主数据：组织 master_schema.organization
-- ============================================================
create table master_schema.organization (
  id uuid primary key,
  org_no varchar(32) not null unique,
  name varchar(200) not null,
  org_type varchar(32) not null,
  parent_id uuid references master_schema.organization(id),
  is_active boolean not null default true,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_org_parent on master_schema.organization(parent_id);

-- ============================================================
-- 主数据：人员 master_schema.employee
-- ============================================================
create table master_schema.employee (
  id uuid primary key,
  employee_no varchar(32) not null unique,
  name varchar(100) not null,
  email varchar(200) not null unique,
  org_id uuid references master_schema.organization(id),
  position varchar(64),
  role varchar(32) not null,
  is_active boolean not null default true,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_employee_org on master_schema.employee(org_id);

-- ============================================================
-- 扩展 supplier_schema：资质、银行账户、敏感变更、绩效
-- ============================================================
create table supplier_schema.qualification (
  id uuid primary key,
  supplier_id uuid not null references supplier_schema.supplier(id),
  type varchar(64) not null,
  certificate_no varchar(128) not null,
  valid_from date,
  valid_to date,
  status varchar(32) not null default 'VALID',
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_qual_supplier on supplier_schema.qualification(supplier_id);

create table supplier_schema.supplier_bank_account (
  id uuid primary key,
  supplier_id uuid not null references supplier_schema.supplier(id),
  bank_name varchar(200) not null,
  account_name varchar(200) not null,
  masked_account_no varchar(32) not null,
  currency varchar(8) not null default 'CNY',
  is_primary boolean not null default false,
  status varchar(32) not null default 'ACTIVE',
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_bank_supplier on supplier_schema.supplier_bank_account(supplier_id);

create table supplier_schema.sensitive_change (
  id uuid primary key,
  supplier_id uuid not null references supplier_schema.supplier(id),
  change_type varchar(32) not null,
  field_name varchar(64) not null,
  old_value varchar(500),
  new_value varchar(500) not null,
  status varchar(32) not null,
  requested_by uuid not null,
  reviewed_by uuid,
  review_reason varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_sensitive_change_type check (change_type in ('BANK_ACCOUNT','TAX_ID','LEGAL_NAME')),
  constraint ck_sensitive_status check (status in ('PENDING','APPROVED','REJECTED'))
);

create table supplier_schema.performance_record (
  id uuid primary key,
  supplier_id uuid not null references supplier_schema.supplier(id),
  period varchar(32) not null,
  delivery_score numeric(5,2) not null default 0,
  quality_score numeric(5,2) not null default 0,
  price_score numeric(5,2) not null default 0,
  compliance_score numeric(5,2) not null default 0,
  total_score numeric(5,2) not null default 0,
  total_orders integer not null default 0,
  remarks varchar(1000),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_perf_supplier on supplier_schema.performance_record(supplier_id);

-- ============================================================
-- 寻源/合同：sourcing_schema
-- ============================================================
create table sourcing_schema.sourcing_project (
  id uuid primary key,
  project_no varchar(32) not null unique,
  title varchar(200) not null,
  demand_type varchar(32) not null,
  category varchar(64),
  budget_amount numeric(19,4),
  currency varchar(8) not null default 'CNY',
  owner_id uuid,
  owner_name varchar(100),
  status varchar(32) not null,
  bid_method varchar(32) not null,
  description varchar(2000),
  planned_close_date date,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_sourcing_status check (status in ('DRAFT','APPROVED','IN_SOURCING','AWARDED','CLOSED','CANCELLED'))
);

create table sourcing_schema.rfq (
  id uuid primary key,
  rfq_no varchar(32) not null unique,
  project_id uuid not null references sourcing_schema.sourcing_project(id),
  title varchar(200) not null,
  version_no integer not null default 1,
  quotation_deadline timestamptz not null,
  status varchar(32) not null,
  currency varchar(8) not null default 'CNY',
  terms varchar(2000),
  delivery_address varchar(500),
  published_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_rfq_status check (status in ('DRAFT','PUBLISHED','QUOTING_CLOSED','EVALUATING','AWARDED','CANCELLED'))
);
create index idx_rfq_project on sourcing_schema.rfq(project_id);

create table sourcing_schema.rfq_invitation (
  id uuid primary key,
  rfq_id uuid not null references sourcing_schema.rfq(id),
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  invited_at timestamptz not null,
  response_status varchar(32) not null default 'PENDING',
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint uk_rfq_supplier unique (rfq_id, supplier_id)
);

create table sourcing_schema.rfq_line (
  id uuid primary key,
  rfq_id uuid not null references sourcing_schema.rfq(id),
  line_no integer not null,
  material_id uuid,
  material_no varchar(32),
  material_name varchar(200) not null,
  specification varchar(500),
  unit varchar(16) not null,
  quantity numeric(19,4) not null,
  estimated_price numeric(19,4),
  remarks varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_rfq_line on sourcing_schema.rfq_line(rfq_id);

create table sourcing_schema.quotation (
  id uuid primary key,
  quotation_no varchar(32) not null unique,
  rfq_id uuid not null references sourcing_schema.rfq(id),
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  total_amount numeric(19,4) not null,
  currency varchar(8) not null default 'CNY',
  delivery_days integer,
  valid_until date,
  submitted_at timestamptz,
  status varchar(32) not null,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint uk_quotation_rfq_supplier unique (rfq_id, supplier_id),
  constraint ck_quotation_status check (status in ('DRAFT','SUBMITTED','EVALUATING','AWARDED','REJECTED','WITHDRAWN'))
);

create table sourcing_schema.quotation_line (
  id uuid primary key,
  quotation_id uuid not null references sourcing_schema.quotation(id),
  line_no integer not null,
  rfq_line_id uuid references sourcing_schema.rfq_line(id),
  material_name varchar(200) not null,
  unit varchar(16) not null,
  quantity numeric(19,4) not null,
  unit_price numeric(19,4) not null,
  amount numeric(19,4) not null,
  remarks varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_quotation_line on sourcing_schema.quotation_line(quotation_id);

create table sourcing_schema.award (
  id uuid primary key,
  award_no varchar(32) not null unique,
  project_id uuid not null references sourcing_schema.sourcing_project(id),
  rfq_id uuid references sourcing_schema.rfq(id),
  quotation_id uuid references sourcing_schema.quotation(id),
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  total_amount numeric(19,4) not null,
  currency varchar(8) not null default 'CNY',
  status varchar(32) not null,
  awarded_at timestamptz,
  decision_remarks varchar(1000),
  approved_by varchar(100),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_award_status check (status in ('PENDING','APPROVED','REJECTED','CANCELLED'))
);

create table sourcing_schema.bpa (
  id uuid primary key,
  bpa_no varchar(32) not null unique,
  title varchar(200) not null,
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  award_id uuid references sourcing_schema.award(id),
  category varchar(64),
  valid_from date not null,
  valid_to date not null,
  total_amount_limit numeric(19,4),
  currency varchar(8) not null default 'CNY',
  payment_terms varchar(200),
  status varchar(32) not null,
  approved_by varchar(100),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_bpa_status check (status in ('DRAFT','ACTIVE','EXPIRED','TERMINATED'))
);

create table master_schema.price_list (
  id uuid primary key,
  price_no varchar(32) not null unique,
  title varchar(200) not null,
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  bpa_id uuid references sourcing_schema.bpa(id),
  material_id uuid,
  material_no varchar(32),
  material_name varchar(200) not null,
  unit_price numeric(19,4) not null,
  currency varchar(8) not null default 'CNY',
  min_quantity numeric(19,4) default 1,
  valid_from date not null,
  valid_to date not null,
  is_active boolean not null default true,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_price_supplier on master_schema.price_list(supplier_id);
create index idx_price_material on master_schema.price_list(material_id);

create table master_schema.supplier_quota (
  id uuid primary key,
  quota_no varchar(32) not null unique,
  title varchar(200) not null,
  category varchar(64),
  bpa_id uuid references sourcing_schema.bpa(id),
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  quota_percent numeric(5,2) not null,
  max_amount numeric(19,4),
  used_amount numeric(19,4) default 0,
  valid_from date not null,
  valid_to date not null,
  is_active boolean not null default true,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_quota_supplier on master_schema.supplier_quota(supplier_id);

-- ============================================================
-- 采购执行：procurement_schema
-- ============================================================
create table procurement_schema.purchase_requisition (
  id uuid primary key,
  pr_no varchar(32) not null unique,
  title varchar(200) not null,
  applicant_id uuid not null,
  applicant_name varchar(100) not null,
  org_id uuid,
  org_name varchar(200),
  demand_type varchar(32) not null,
  sourcing_project_id uuid,
  bpa_id uuid,
  price_list_id uuid,
  supplier_id uuid,
  supplier_no varchar(32),
  supplier_name varchar(200),
  required_date date,
  currency varchar(8) not null default 'CNY',
  total_amount numeric(19,4) not null default 0,
  status varchar(32) not null,
  description varchar(2000),
  approved_by varchar(100),
  approved_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_pr_demand check (demand_type in ('CATALOG','SOURCING','FRAMEWORK_RELEASE','EMERGENCY')),
  constraint ck_pr_status check (status in ('DRAFT','PENDING_APPROVAL','BUDGET_CHECKING','APPROVED','REJECTED','CANCELLED','CONVERTED'))
);
create index idx_pr_applicant on procurement_schema.purchase_requisition(applicant_id);
create index idx_pr_status on procurement_schema.purchase_requisition(status);

create table procurement_schema.pr_line (
  id uuid primary key,
  pr_id uuid not null references procurement_schema.purchase_requisition(id),
  line_no integer not null,
  material_id uuid,
  material_no varchar(32),
  material_name varchar(200) not null,
  specification varchar(500),
  unit varchar(16) not null,
  quantity numeric(19,4) not null,
  estimated_unit_price numeric(19,4) not null,
  estimated_amount numeric(19,4) not null,
  required_date date,
  remarks varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_pr_line_pr on procurement_schema.pr_line(pr_id);

create table procurement_schema.purchase_order (
  id uuid primary key,
  po_no varchar(32) not null unique,
  title varchar(200) not null,
  pr_id uuid references procurement_schema.purchase_requisition(id),
  pr_no varchar(32),
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  buyer_id uuid,
  buyer_name varchar(100),
  org_id uuid,
  org_name varchar(200),
  contract_no varchar(64),
  currency varchar(8) not null default 'CNY',
  total_amount numeric(19,4) not null default 0,
  status varchar(32) not null,
  order_date date not null,
  expected_date date,
  delivery_address varchar(500),
  payment_terms varchar(200),
  approved_by varchar(100),
  approved_at timestamptz,
  sent_at timestamptz,
  acknowledged_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_po_status check (status in ('DRAFT','PENDING_APPROVAL','APPROVED','SENT','ACKNOWLEDGED','PARTIALLY_FULFILLED','FULFILLED','CLOSED','REJECTED','CANCELLED'))
);
create index idx_po_supplier on procurement_schema.purchase_order(supplier_id);
create index idx_po_status on procurement_schema.purchase_order(status);

create table procurement_schema.po_line (
  id uuid primary key,
  po_id uuid not null references procurement_schema.purchase_order(id),
  line_no integer not null,
  material_id uuid,
  material_no varchar(32),
  material_name varchar(200) not null,
  specification varchar(500),
  unit varchar(16) not null,
  quantity numeric(19,4) not null,
  unit_price numeric(19,4) not null,
  amount numeric(19,4) not null,
  received_quantity numeric(19,4) not null default 0,
  required_date date,
  remarks varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_po_line_po on procurement_schema.po_line(po_id);

create table procurement_schema.goods_receipt (
  id uuid primary key,
  gr_no varchar(32) not null unique,
  po_id uuid not null references procurement_schema.purchase_order(id),
  po_no varchar(32) not null,
  supplier_id uuid not null,
  supplier_name varchar(200) not null,
  received_at timestamptz not null,
  receiver_id uuid,
  receiver_name varchar(100),
  inspector_id uuid,
  inspector_name varchar(100),
  status varchar(32) not null,
  total_received_quantity numeric(19,4) not null default 0,
  remarks varchar(1000),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_gr_status check (status in ('DRAFT','PENDING_INSPECTION','ACCEPTED','PARTIALLY_ACCEPTED','REJECTED','RETURNED'))
);
create index idx_gr_po on procurement_schema.goods_receipt(po_id);

create table procurement_schema.receipt_line (
  id uuid primary key,
  gr_id uuid not null references procurement_schema.goods_receipt(id),
  line_no integer not null,
  po_line_id uuid references procurement_schema.po_line(id),
  material_name varchar(200) not null,
  unit varchar(16) not null,
  received_quantity numeric(19,4) not null,
  accepted_quantity numeric(19,4) not null default 0,
  rejected_quantity numeric(19,4) not null default 0,
  batch_no varchar(64),
  inspection_result varchar(32),
  inspection_remarks varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_receipt_line_gr on procurement_schema.receipt_line(gr_id);

create table procurement_schema.service_entry (
  id uuid primary key,
  ses_no varchar(32) not null unique,
  po_id uuid not null references procurement_schema.purchase_order(id),
  po_no varchar(32) not null,
  supplier_id uuid not null,
  supplier_name varchar(200) not null,
  service_period_start date,
  service_period_end date,
  acceptor_id uuid,
  acceptor_name varchar(100),
  status varchar(32) not null,
  total_amount numeric(19,4) not null default 0,
  currency varchar(8) not null default 'CNY',
  remarks varchar(2000),
  approved_by varchar(100),
  approved_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_ses_status check (status in ('DRAFT','PENDING_ACCEPTANCE','APPROVED','REJECTED','REVOKED'))
);

create table procurement_schema.service_entry_line (
  id uuid primary key,
  ses_id uuid not null references procurement_schema.service_entry(id),
  line_no integer not null,
  description varchar(500) not null,
  milestone varchar(200),
  quantity numeric(19,4) not null,
  unit varchar(16) not null,
  unit_price numeric(19,4) not null,
  amount numeric(19,4) not null,
  evidence_url varchar(500),
  accepted_quantity numeric(19,4) not null default 0,
  accepted_amount numeric(19,4) not null default 0,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_ses_line on procurement_schema.service_entry_line(ses_id);

-- ============================================================
-- 结算：settlement_schema
-- ============================================================
create table settlement_schema.invoice (
  id uuid primary key,
  invoice_no varchar(32) not null unique,
  invoice_code varchar(32) not null,
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  po_id uuid references procurement_schema.purchase_order(id),
  po_no varchar(32),
  gr_id uuid references procurement_schema.goods_receipt(id),
  gr_no varchar(32),
  invoice_date date not null,
  seller_tax_id varchar(32) not null,
  total_amount numeric(19,4) not null,
  tax_amount numeric(19,4) not null default 0,
  currency varchar(8) not null default 'CNY',
  status varchar(32) not null,
  tax_verified_at timestamptz,
  matched_at timestamptz,
  posted_at timestamptz,
  remarks varchar(1000),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_invoice_status check (status in ('CAPTURED','TAX_CHECKING','VERIFIED','MATCHING','MATCHED','BLOCKED','POSTED','CANCELLED')),
  constraint uk_invoice unique (invoice_code, invoice_no, seller_tax_id)
);
create index idx_invoice_supplier on settlement_schema.invoice(supplier_id);
create index idx_invoice_status on settlement_schema.invoice(status);

create table settlement_schema.invoice_line (
  id uuid primary key,
  invoice_id uuid not null references settlement_schema.invoice(id),
  line_no integer not null,
  po_line_id uuid references procurement_schema.po_line(id),
  description varchar(500) not null,
  quantity numeric(19,4) not null,
  unit_price numeric(19,4) not null,
  amount numeric(19,4) not null,
  tax_rate numeric(5,2) not null,
  tax_amount numeric(19,4) not null,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_invoice_line on settlement_schema.invoice_line(invoice_id);

create table settlement_schema.payment_request (
  id uuid primary key,
  payment_no varchar(32) not null unique,
  invoice_id uuid not null references settlement_schema.invoice(id),
  invoice_no varchar(32) not null,
  supplier_id uuid not null,
  supplier_name varchar(200) not null,
  payee_account_id uuid,
  payee_account_no varchar(64),
  payee_bank_name varchar(200),
  payment_amount numeric(19,4) not null,
  currency varchar(8) not null default 'CNY',
  payment_method varchar(32) not null,
  requested_by_id uuid,
  requested_by_name varchar(100),
  approved_by varchar(100),
  status varchar(32) not null,
  submitted_at timestamptz,
  paid_at timestamptz,
  external_ref_no varchar(64),
  failure_reason varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_payment_status check (status in ('DRAFT','PENDING_APPROVAL','SUBMITTED','PROCESSING','PAID','FAILED','CANCELLED'))
);
create index idx_payment_invoice on settlement_schema.payment_request(invoice_id);
create index idx_payment_status on settlement_schema.payment_request(status);
