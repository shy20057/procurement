create table integration_schema.job_definition (
  job_code varchar(80) primary key,
  entity_type varchar(30) not null unique,
  name varchar(100) not null,
  enabled boolean not null default true,
  cron_expression varchar(100) not null,
  zone_id varchar(40) not null default 'Asia/Shanghai'
);
create table integration_schema.job_run (
  run_id uuid primary key,
  job_code varchar(80) not null references integration_schema.job_definition(job_code),
  mode varchar(20) not null check (mode in ('AUTO','RECONCILE','REPAIR')),
  trigger_type varchar(20) not null,
  parent_run_id uuid,
  request_id varchar(100) not null unique,
  reason varchar(500) not null,
  status varchar(30) not null,
  attempt_no int not null default 0,
  next_retry_at timestamptz,
  owner_id varchar(100),
  fence_token bigint,
  phase varchar(30),
  fetched_count bigint not null default 0,
  applied_count bigint not null default 0,
  ignored_count bigint not null default 0,
  total_count bigint not null default 0,
  error_code varchar(60),
  error_summary varchar(500),
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default clock_timestamp(),
  check (status in ('PENDING','RUNNING','SUCCEEDED','RETRY_WAIT','FAILED',
                   'CANCEL_REQUESTED','CANCELLED'))
);
create index idx_job_run_due on integration_schema.job_run(status, next_retry_at, created_at);
create table integration_schema.job_request_key (
  request_id varchar(100) primary key,
  run_id uuid not null references integration_schema.job_run(run_id),
  job_code varchar(80) not null,
  mode varchar(20) not null
);
create table integration_schema.management_audit (
  id uuid primary key,
  job_code varchar(80) not null,
  operation varchar(60) not null,
  reason varchar(500) not null,
  created_at timestamptz not null default clock_timestamp()
);
create table integration_schema.job_lease (
  resource_key varchar(80) primary key,
  owner_id varchar(100),
  fence_token bigint not null default 0,
  lease_until timestamptz not null default '-infinity',
  heartbeat_at timestamptz
);
insert into integration_schema.job_definition(job_code, entity_type, name, cron_expression) values
 ('SAP_COST_CENTER_DELTA','COST_CENTER','SAP 成本中心','0 */10 * * * *'),
 ('SAP_EXCHANGE_RATE_DELTA','EXCHANGE_RATE','SAP 汇率','0 10 2 * * *');
insert into integration_schema.sync_checkpoint(job_code)
 select job_code from integration_schema.job_definition;
insert into integration_schema.job_lease(resource_key)
 select job_code from integration_schema.job_definition;
