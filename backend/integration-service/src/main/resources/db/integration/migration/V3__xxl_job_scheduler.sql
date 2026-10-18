alter table integration_schema.job_definition
  add column handler_code varchar(100),
  add column resource_key varchar(100);

update integration_schema.job_definition
set handler_code = case job_code
      when 'SAP_COST_CENTER_DELTA' then 'sapCostCenterSyncJobHandler'
      when 'SAP_EXCHANGE_RATE_DELTA' then 'sapExchangeRateSyncJobHandler'
    end,
    resource_key = case job_code
      when 'SAP_COST_CENTER_DELTA' then 'SAP_COST_CENTER_WRITER'
      when 'SAP_EXCHANGE_RATE_DELTA' then 'SAP_EXCHANGE_RATE_WRITER'
    end,
    cron_expression = case job_code
      when 'SAP_COST_CENTER_DELTA' then '0 */10 * * * ?'
      when 'SAP_EXCHANGE_RATE_DELTA' then '0 10 2 * * ?'
    end;

alter table integration_schema.job_definition
  alter column handler_code set not null,
  alter column resource_key set not null;

alter table integration_schema.job_run
  add column xxl_job_id bigint,
  add column xxl_log_key varchar(64),
  add column xxl_shard_index int,
  add column xxl_shard_total int,
  add column executor_address varchar(255),
  alter column request_id type varchar(180);

alter table integration_schema.job_request_key
  alter column request_id type varchar(180);

create unique index uk_job_run_xxl_log_shard
  on integration_schema.job_run(job_code, xxl_job_id, xxl_log_key, xxl_shard_index)
  where xxl_log_key is not null;

create index idx_job_run_local_due
  on integration_schema.job_run(status, next_retry_at, created_at)
  where trigger_type <> 'XXL';

create index idx_job_run_xxl_identity
  on integration_schema.job_run(job_code, xxl_job_id, xxl_log_key, xxl_shard_index)
  where trigger_type = 'XXL';

insert into integration_schema.job_lease(resource_key) values
  ('SAP_COST_CENTER_WRITER'),
  ('SAP_EXCHANGE_RATE_WRITER')
on conflict(resource_key) do nothing;
