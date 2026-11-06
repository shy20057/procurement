create table mock_sap_schema.purchase_order (
  id bigint generated always as identity primary key,
  order_no varchar(40) not null unique,
  title varchar(200) not null,
  supplier_code varchar(40) not null,
  purchasing_org varchar(32) not null,
  order_date date not null,
  currency varchar(8) not null,
  publication_status varchar(16) not null
    check (publication_status in ('PUBLISHED','WITHDRAWN','CANCELLED')),
  published_updated_at timestamptz(6) not null
);
create index idx_mock_po_published_cursor
  on mock_sap_schema.purchase_order(published_updated_at,id);

create table mock_sap_schema.purchase_order_line (
  order_id bigint not null references mock_sap_schema.purchase_order(id),
  line_no varchar(16) not null check (line_no ~ '^[0-9]{5}$'),
  material_code varchar(32) not null,
  material_name varchar(200) not null,
  quantity numeric(19,4) not null check (quantity > 0),
  unit varchar(16) not null,
  unit_price numeric(19,4) not null check (unit_price >= 0),
  amount numeric(19,4) not null check (amount >= 0),
  required_date date not null,
  line_status varchar(16) not null check (line_status in ('ACTIVE','CANCELLED')),
  primary key (order_id,line_no)
);
