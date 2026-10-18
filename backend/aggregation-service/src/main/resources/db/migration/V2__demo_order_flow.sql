create schema if not exists workflow_schema;

create table workflow_schema.demo_order_flow (
  id uuid primary key,
  flow_no varchar(32) not null unique,
  demand_no varchar(32) not null unique,
  rfq_no varchar(32) unique,
  quotation_no varchar(32) unique,
  award_no varchar(32) unique,
  pr_no varchar(32) unique,
  po_no varchar(32) unique,
  receipt_no varchar(32) unique,
  invoice_no varchar(32) unique,
  payment_no varchar(32) unique,
  supplier_id uuid not null,
  supplier_no varchar(32) not null,
  supplier_name varchar(200) not null,
  demand_title varchar(200) not null,
  item_name varchar(200) not null,
  quantity integer not null check (quantity > 0),
  unit_price numeric(19,4) not null check (unit_price > 0),
  total_amount numeric(19,4) not null check (total_amount > 0),
  status varchar(32) not null,
  last_action varchar(200) not null,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint ck_demo_order_status check (status in (
    'DEMAND_CREATED','RFQ_PUBLISHED','QUOTATION_RECEIVED','AWARDED',
    'PR_APPROVED','PO_ISSUED','GOODS_RECEIVED','INVOICE_MATCHED','PAYMENT_COMPLETED'
  ))
);
create index idx_demo_order_status_updated on workflow_schema.demo_order_flow(status, updated_at desc);

insert into supplier_schema.supplier (
  id, supplier_no, legal_name, uscc, contact_name, contact_email,
  status, review_reason, version, created_at, updated_at
) values (
  '10000000-0000-0000-0000-000000000001', 'SUP-DEMO-001', '演示工业用品有限公司',
  '91310000DEMO000001', '演示联系人', 'demo-supplier@example.com',
  'ACTIVE', '系统初始化的闭环演示供应商', 0, current_timestamp, current_timestamp
) on conflict (uscc) do nothing;

