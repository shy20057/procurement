-- 新模式不解释或复用旧变更流检查点。此项目已批准清空同步模块演示数据。
-- 不使用 CASCADE，防止意外波及其他业务 schema 的引用。
alter table integration_schema.sync_checkpoint add column generation bigint not null default 1;
truncate table integration_schema.job_request_key, integration_schema.sync_page_audit,
 integration_schema.sync_page_failure, integration_schema.sync_alert,
 integration_schema.sync_reconciliation_seen, integration_schema.sync_reconciliation_diff,
 integration_schema.management_audit, integration_schema.job_run,
 integration_schema.cost_center, integration_schema.exchange_rate restart identity;

alter table integration_schema.cost_center drop column source_version;
alter table integration_schema.cost_center rename column source_changed_at to source_updated_at;
alter table integration_schema.cost_center rename column deleted to is_deleted;
alter table integration_schema.cost_center
 add column source_system varchar(40) not null default 'SAP',
 add column source_id bigint not null,
 add column enabled boolean not null default true,
 add unique(source_system,source_id);
alter table integration_schema.exchange_rate drop column source_version;
alter table integration_schema.exchange_rate rename column source_changed_at to source_updated_at;
alter table integration_schema.exchange_rate rename column deleted to is_deleted;
alter table integration_schema.exchange_rate
 add column source_system varchar(40) not null default 'SAP',
 add column source_id bigint not null,
 add column blocked boolean not null default false,
 add column enabled boolean not null default true,
 add unique(source_system,source_id);

alter table integration_schema.sync_reconciliation_seen
 add column source_id bigint not null,
 add column source_updated_at timestamptz(6) not null,
 add column source_hash char(64) not null,
 add column observed_at timestamptz not null default clock_timestamp(),
 add unique(run_id,source_id);
alter table integration_schema.sync_reconciliation_diff
 drop column source_version, drop column target_version,
 add column source_id bigint,
 add column source_updated_at timestamptz(6),
 add column target_updated_at timestamptz(6),
 add column source_hash char(64), add column target_hash char(64),
 add column verification_status varchar(30) not null default 'OBSERVED',
 add column repair_status varchar(40) not null default 'NOT_REQUESTED',
 add column detail varchar(1000),
 add column verified_at timestamptz,
 add column repaired_at timestamptz;
alter table integration_schema.job_run
 add column scan_context text,
 add column window_id uuid,
 add column target_watermark timestamptz(6),
 add column caught_up boolean not null default false,
 add column checkpoint_generation bigint not null default 1;
alter table integration_schema.job_request_key add column request_fingerprint varchar(64);
alter table integration_schema.job_definition
 add column sync_mode varchar(30) not null default 'TIME_WINDOW_V2';
alter table integration_schema.management_audit add column actor varchar(100) not null default 'LOCAL_ADMIN';
update integration_schema.sync_checkpoint set committed_cursor=null,baseline_complete=false,
 last_success_at=null,version=version+1;
update integration_schema.job_lease set owner_id=null,lease_until='-infinity',fence_token=fence_token+1;
