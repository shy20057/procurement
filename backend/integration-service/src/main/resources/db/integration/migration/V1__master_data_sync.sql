create schema if not exists integration_schema;

create table integration_schema.cost_center (
  id bigserial primary key,
  controlling_area varchar(20) not null,
  cost_center_code varchar(40) not null,
  company_code varchar(20) not null,
  name varchar(200) not null,
  valid_from date not null,
  valid_to date not null,
  blocked boolean not null,
  deleted boolean not null default false,
  source_version bigint not null,
  source_changed_at timestamptz not null,
  payload_hash varchar(64) not null,
  synced_at timestamptz not null default clock_timestamp(),
  unique (controlling_area, cost_center_code),
  check (valid_from <= valid_to)
);

create table integration_schema.exchange_rate (
  id bigserial primary key,
  rate_type varchar(20) not null,
  from_currency varchar(10) not null,
  to_currency varchar(10) not null,
  effective_date date not null,
  raw_rate numeric(28,12) not null,
  from_factor numeric(19,4) not null check (from_factor > 0),
  to_factor numeric(19,4) not null check (to_factor > 0),
  quotation_method varchar(20) not null,
  normalized_rate numeric(28,12) not null check (normalized_rate > 0),
  deleted boolean not null default false,
  source_version bigint not null,
  source_changed_at timestamptz not null,
  payload_hash varchar(64) not null,
  synced_at timestamptz not null default clock_timestamp(),
  unique (rate_type, from_currency, to_currency, effective_date)
);

create table integration_schema.sync_checkpoint (
  job_code varchar(80) primary key,
  committed_cursor text,
  baseline_complete boolean not null default false,
  last_success_at timestamptz,
  version bigint not null default 0
);

create table integration_schema.sync_page_audit (
  id bigserial primary key,
  run_id uuid not null,
  page_no int not null,
  phase varchar(30) not null,
  from_cursor text,
  to_cursor text,
  fetched_count int not null,
  applied_count int not null,
  ignored_count int not null,
  committed_at timestamptz not null default clock_timestamp(),
  unique (run_id, page_no)
);

create table integration_schema.sync_page_failure (
  id bigserial primary key,
  run_id uuid not null,
  attempt_no int not null,
  from_cursor text,
  error_code varchar(60) not null,
  error_summary varchar(500) not null,
  occurred_at timestamptz not null default clock_timestamp()
);

create table integration_schema.sync_alert (
  id bigserial primary key,
  job_code varchar(80) not null,
  run_id uuid,
  category varchar(60) not null,
  severity varchar(20) not null,
  dedup_key varchar(180) not null unique,
  first_seen_at timestamptz not null default clock_timestamp(),
  last_seen_at timestamptz not null default clock_timestamp(),
  occurrence_count bigint not null default 1,
  status varchar(20) not null default 'OPEN',
  summary varchar(500) not null,
  check (status in ('OPEN','ACKNOWLEDGED','RESOLVED'))
);

create table integration_schema.sync_reconciliation_seen (
  run_id uuid not null,
  business_key varchar(240) not null,
  primary key (run_id, business_key)
);

create table integration_schema.sync_reconciliation_diff (
  id bigserial primary key,
  run_id uuid not null,
  business_key varchar(240) not null,
  difference_type varchar(30) not null,
  source_version bigint,
  target_version bigint,
  repaired boolean not null default false,
  created_at timestamptz not null default clock_timestamp(),
  unique (run_id, business_key)
);
