alter table procurement_schema.purchase_order
  add column source_system varchar(32),
  add column source_id bigint,
  add column source_order_no varchar(40),
  add column source_supplier_code varchar(40),
  add column source_purchasing_org varchar(32),
  add column source_updated_at timestamptz(6),
  add column source_publication_status varchar(16),
  add column source_payload_hash char(64),
  add column synced_at timestamptz(6),
  add constraint ck_po_source_complete check (
    (source_system is null and source_id is null and source_order_no is null
      and source_supplier_code is null and source_purchasing_org is null and source_updated_at is null
      and source_publication_status is null and source_payload_hash is null and synced_at is null)
    or
    (source_system is not null and source_id is not null and source_id > 0
      and source_order_no is not null and source_supplier_code is not null and source_purchasing_org is not null
      and source_updated_at is not null and source_publication_status is not null
      and source_publication_status in ('PUBLISHED','WITHDRAWN','CANCELLED')
      and source_payload_hash is not null and synced_at is not null)
  );

create sequence procurement_schema.external_po_no_seq;
-- 兼容历史数据中已经使用的 EXT- 编号；保留原编号和订单 UUID。
select setval('procurement_schema.external_po_no_seq',
              coalesce(max(case when po_no ~ '^EXT-[0-9]{1,18}$'
                                then substring(po_no from 5)::bigint end), 0) + 1, false)
from procurement_schema.purchase_order;

create unique index uk_po_source_id
  on procurement_schema.purchase_order(source_system,source_id)
  where source_system is not null;
create unique index uk_po_source_no
  on procurement_schema.purchase_order(source_system,source_order_no)
  where source_system is not null;
create index idx_po_supplier_source_version
  on procurement_schema.purchase_order(supplier_id,source_updated_at desc,id desc)
  where source_system is not null;

alter table procurement_schema.po_line
  add column source_line_no varchar(16),
  add column line_status varchar(16) not null default 'ACTIVE'
    check (line_status in ('ACTIVE','CANCELLED'));
create unique index uk_po_source_line
  on procurement_schema.po_line(po_id,source_line_no)
  where source_line_no is not null;
