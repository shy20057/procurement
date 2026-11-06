insert into integration_schema.job_definition
  (job_code,entity_type,name,enabled,cron_expression,handler_code,resource_key,sync_mode)
values ('SAP_PURCHASE_ORDER_DELTA','PURCHASE_ORDER','SAP 采购订单',false,'0 0/5 * * * ?',
        'sapPurchaseOrderSyncJobHandler','SAP_PURCHASE_ORDER_WRITER','PO_TIME_WINDOW_V1');

insert into integration_schema.sync_checkpoint(job_code) values ('SAP_PURCHASE_ORDER_DELTA');
insert into integration_schema.job_lease(resource_key) values ('SAP_PURCHASE_ORDER_WRITER');

-- The order workflow replaces scheduling of the previous demonstration datasets.
update integration_schema.job_definition set enabled=false
where job_code in ('SAP_COST_CENTER_DELTA','SAP_EXCHANGE_RATE_DELTA');
