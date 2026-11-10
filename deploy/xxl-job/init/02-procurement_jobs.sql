USE `xxl_job`;

-- Initialization files run in separate MySQL sessions; declare UTF-8 here too.
SET NAMES utf8mb4;

INSERT INTO `xxl_job_group` (`app_name`, `title`, `address_type`, `address_list`, `update_time`)
SELECT 'procurement-integration-executor', '采购集成执行器', 0, NULL, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `xxl_job_group` WHERE `app_name` = 'procurement-integration-executor'
);

INSERT INTO `xxl_job_info` (
  `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
  `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
  `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
  `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
  `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT executor_group.id, jobs.description, NOW(), NOW(), 'SRM', '',
       jobs.schedule_type, jobs.schedule_conf, 'DO_NOTHING', 'FAILOVER',
       jobs.handler, '', 'SERIAL_EXECUTION', jobs.timeout_seconds,
       jobs.retry_count, 'BEAN', '', '通过代码发布', NOW(), '', 0, 0, 0
FROM (
  SELECT id FROM `xxl_job_group` WHERE app_name = 'procurement-integration-executor' ORDER BY id LIMIT 1
) executor_group
CROSS JOIN (
  SELECT 'SAP 采购订单同步' AS description, 'CRON' AS schedule_type, '0 0/5 * * * ?' AS schedule_conf,
         'sapPurchaseOrderSyncJobHandler' AS handler, 1800 AS timeout_seconds, 2 AS retry_count
  UNION ALL SELECT 'SAP 采购订单对账', 'CRON', '0 30 2 * * ?', 'sapPurchaseOrderReconcileJobHandler', 3600, 1
  UNION ALL SELECT 'SAP 采购订单指定修复', 'NONE', '', 'sapPurchaseOrderRepairJobHandler', 120, 0
) jobs
WHERE NOT EXISTS (
  SELECT 1 FROM `xxl_job_info` current_job
  WHERE current_job.job_group = executor_group.id AND current_job.executor_handler = jobs.handler
);
