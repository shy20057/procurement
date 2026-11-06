-- Explicit demo seed, run only by the approved rebuild utility after real Flyway migrations.
-- A single materialized database time gives all six rows an identical microsecond timestamp.
with seed_time as materialized (
  select (clock_timestamp()-interval '10 minutes')::timestamptz(6) as t
), data(entity_type,business_key,payload,blocked,enabled,is_deleted) as (
  values
    ('COST_CENTER','["A100","RD001"]',
      jsonb_build_object('controllingArea','A100','costCenterCode','RD001','companyCode','1000','name','研发一部','validFrom',date_trunc('year',current_date)::date,'validTo','2099-12-31'),false,true,false),
    ('COST_CENTER','["A100","MFG001"]',
      jsonb_build_object('controllingArea','A100','costCenterCode','MFG001','companyCode','1000','name','制造中心','validFrom',date_trunc('year',current_date)::date,'validTo','2099-12-31'),false,true,false),
    ('COST_CENTER','["A100","FIN001"]',
      jsonb_build_object('controllingArea','A100','costCenterCode','FIN001','companyCode','1000','name','财务共享中心','validFrom',date_trunc('year',current_date)::date,'validTo','2099-12-31'),false,true,false),
    ('EXCHANGE_RATE',format('["M","USD","CNY","%s"]',current_date),
      jsonb_build_object('rateType','M','fromCurrency','USD','toCurrency','CNY','effectiveDate',current_date,'rawRate',7.1000,'fromFactor',1,'toFactor',1,'quotationMethod','DIRECT','normalizedRate',7.100000000000),false,true,false),
    ('EXCHANGE_RATE',format('["M","EUR","CNY","%s"]',current_date),
      jsonb_build_object('rateType','M','fromCurrency','EUR','toCurrency','CNY','effectiveDate',current_date,'rawRate',7.8000,'fromFactor',1,'toFactor',1,'quotationMethod','DIRECT','normalizedRate',7.800000000000),false,true,false),
    ('EXCHANGE_RATE',format('["M","JPY","CNY","%s"]',current_date),
      jsonb_build_object('rateType','M','fromCurrency','JPY','toCurrency','CNY','effectiveDate',current_date,'rawRate',0.0480,'fromFactor',1,'toFactor',1,'quotationMethod','DIRECT','normalizedRate',0.048000000000),false,true,false)
)
insert into mock_sap_schema.sap_master_data(entity_type,business_key,payload,blocked,enabled,is_deleted,created_at,updated_at)
select entity_type,business_key,payload-'blocked'-'enabled'-'isDeleted',blocked,enabled,is_deleted,t,t
from data cross join seed_time;
