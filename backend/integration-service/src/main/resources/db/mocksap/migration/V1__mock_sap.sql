create schema if not exists mock_sap_schema;
create table mock_sap_schema.stream_clock (
  entity_type varchar(30) primary key,
  sequence_no bigint not null default 0,
  unavailable boolean not null default false
);
insert into mock_sap_schema.stream_clock(entity_type) values ('COST_CENTER'),('EXCHANGE_RATE');
create table mock_sap_schema.master_record (
  entity_type varchar(30) not null,
  business_key varchar(240) not null,
  source_version bigint not null,
  record_json text not null,
  primary key(entity_type,business_key)
);
create table mock_sap_schema.change_event (
  entity_type varchar(30) not null,
  sequence_no bigint not null,
  record_json text not null,
  primary key(entity_type,sequence_no)
);
create table mock_sap_schema.snapshot (
  id uuid primary key,
  entity_type varchar(30) not null,
  baseline_cursor varchar(80) not null,
  record_count bigint not null,
  created_at timestamptz not null default clock_timestamp()
);
create table mock_sap_schema.snapshot_item (
  snapshot_id uuid not null references mock_sap_schema.snapshot(id),
  item_no bigint not null,
  record_json text not null,
  primary key(snapshot_id,item_no)
);
