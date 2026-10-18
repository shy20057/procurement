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
SELECT `id`, 'SAP成本中心增量同步', NOW(), NOW(), 'SRM', '',
       'CRON', '0 */10 * * * ?', 'DO_NOTHING', 'FAILOVER',
       'sapCostCenterSyncJobHandler', '', 'SERIAL_EXECUTION', 1800,
       2, 'BEAN', '', '通过代码发布', NOW(), '', 0, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'procurement-integration-executor'
  AND NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'sapCostCenterSyncJobHandler'
  )
ORDER BY `id`
LIMIT 1;

INSERT INTO `xxl_job_info` (
  `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
  `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
  `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
  `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
  `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`
)
SELECT `id`, 'SAP汇率增量同步', NOW(), NOW(), 'SRM', '',
       'CRON', '0 10 2 * * ?', 'FIRE_ONCE_NOW', 'FAILOVER',
       'sapExchangeRateSyncJobHandler', '', 'SERIAL_EXECUTION', 900,
       2, 'BEAN', '', '通过代码发布', NOW(), '', 0, 0, 0
FROM `xxl_job_group`
WHERE `app_name` = 'procurement-integration-executor'
  AND NOT EXISTS (
    SELECT 1 FROM `xxl_job_info` WHERE `executor_handler` = 'sapExchangeRateSyncJobHandler'
  )
ORDER BY `id`
LIMIT 1;
