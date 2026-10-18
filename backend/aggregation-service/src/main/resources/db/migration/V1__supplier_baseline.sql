create schema if not exists supplier_schema;

create table supplier_schema.supplier (
  id uuid primary key,
  supplier_no varchar(32) not null unique,
  legal_name varchar(200) not null,
  uscc varchar(18) not null,
  contact_name varchar(100) not null,
  contact_email varchar(200) not null,
  status varchar(32) not null,
  review_reason varchar(500),
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint uk_supplier_uscc unique (uscc),
  constraint ck_supplier_status check (status in ('DRAFT','PENDING_REVIEW','ACTIVE','SUSPENDED','REJECTED','EXITED'))
);
create index idx_supplier_status_created on supplier_schema.supplier(status, created_at desc);

create table supplier_schema.outbox_event (
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
create index idx_outbox_pending on supplier_schema.outbox_event(next_retry_at, occurred_at) where published_at is null;

