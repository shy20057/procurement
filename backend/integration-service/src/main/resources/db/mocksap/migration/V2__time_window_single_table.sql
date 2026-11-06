-- Current source state only. Keep the historical V1 migration immutable.
create table mock_sap_schema.sap_master_data (
  id bigint generated always as identity primary key,
  entity_type varchar(30) not null check (entity_type in ('COST_CENTER','EXCHANGE_RATE')),
  business_key varchar(240) not null,
  payload jsonb not null check (jsonb_typeof(payload)='object'),
  blocked boolean not null default false,
  enabled boolean not null default true,
  is_deleted boolean not null default false,
  created_at timestamptz(6) not null default clock_timestamp(),
  updated_at timestamptz(6) not null default clock_timestamp(),
  unique(entity_type,business_key),
  check(updated_at >= created_at)
);
create index idx_sap_master_delta on mock_sap_schema.sap_master_data(entity_type,updated_at,id);
create index idx_sap_master_full on mock_sap_schema.sap_master_data(entity_type,id);

-- Read only current legacy JSON, never versions/events. Build compact, escaped JSON-array keys.
-- Legacy deleted was a logical deletion flag; enabled did not exist and defaults to true.
with current_values as (
  select entity_type, record_json::jsonb as record,
    case when entity_type='COST_CENTER' then record_json::jsonb->'costCenter'
         else record_json::jsonb->'exchangeRate' end as data
  from mock_sap_schema.master_record
), converted as (
  select entity_type, data,
    case when entity_type='COST_CENTER'
      then format('[%s,%s]',to_json(data->>'controllingArea'),to_json(data->>'costCenterCode'))
      else format('[%s,%s,%s,%s]',to_json(data->>'rateType'),to_json(data->>'fromCurrency'),
                  to_json(data->>'toCurrency'),to_json(data->>'effectiveDate')) end as business_key,
    coalesce((record->>'changedAt')::timestamptz,clock_timestamp()-interval '10 minutes') as source_time
  from current_values
)
insert into mock_sap_schema.sap_master_data(entity_type,business_key,payload,blocked,enabled,is_deleted,created_at,updated_at)
select entity_type,business_key,data-'blocked'-'enabled'-'deleted'-'isDeleted',
       coalesce((data->>'blocked')::boolean,false),coalesce((data->>'enabled')::boolean,true),
       coalesce((data->>'isDeleted')::boolean,(data->>'deleted')::boolean,false),source_time,source_time
from converted order by entity_type,business_key;

-- No cascade: a dependency outside this legacy subsystem must block migration for review.
drop table mock_sap_schema.snapshot_item;
drop table mock_sap_schema.snapshot;
drop table mock_sap_schema.change_event;
drop table mock_sap_schema.master_record;
drop table mock_sap_schema.stream_clock;
