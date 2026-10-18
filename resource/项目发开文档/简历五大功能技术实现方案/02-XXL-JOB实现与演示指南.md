# XXL-JOB实现与演示指南

> 更新日期：2026-10-18。
> 本项目约定：执行测试、构建、Docker启动、数据库迁移和联调前先获得用户批准。

## 1. 已实现范围

- XXL-JOB Admin 2.4.2与专用MySQL 8；
- 成本中心`SAP_COST_CENTER_DELTA`与汇率`SAP_EXCHANGE_RATE_DELTA`两个BEAN Handler；
- 开发聚合模式在`aggregation-service`中内嵌执行器；
- 线上微服务模式由多个`integration-service`实例注册同一AppName；
- XXL调度身份与SRM业务运行映射；
- dataset resource lease、fencing token、断点检查点与幂等落库；
- XXL失败交给XXL重试，SRM人工失败保留本地持久化退避；
- SRM前端展示Admin入口、Handler、资源键、XXL任务ID、日志摘要与执行节点。

## 2. 端口与职责

| 端口 | 组件 | 职责 |
| --- | --- | --- |
| 3307 | XXL-JOB MySQL | 调度定义、注册和日志 |
| 9090 | XXL-JOB Admin | Cron、路由、超时、重试与运维界面 |
| 9999 | XXL Executor | 接收Admin调度回调 |
| 8090 | Aggregation Service | 开发模式聚合业务接口 |
| 8080 | API Gateway | 前端统一API入口 |
| 5173 | Vue | 数据同步可视化页面 |

PostgreSQL保存SRM业务运行、租约、检查点和主数据。MySQL与PostgreSQL之间没有分布式事务。

## 3. 开发环境启动顺序

获得启动许可后：

1. 使用`deploy/docker-compose.yml`启动基础设施；
2. 从IDE启动`AggregationServiceApplication`，确认8090与9999没有被占用；
3. 启动`ApiGatewayApplication`，使用`aggregation` profile；
4. 启动Vue，访问`http://localhost:5173/integration/sync`；
5. 打开`http://localhost:9090/xxl-job-admin`；
6. 修改Admin默认密码；
7. 确认执行器`procurement-integration-executor`在线；
8. 检查两个种子任务后再手动启用。

两个种子任务默认`trigger_status=0`，因为初始化SQL不应猜测Admin计算的`trigger_next_time`。MySQL初始化脚本只在数据卷首次创建时执行；旧数据卷不会自动重放脚本。

开发模式不要启动`IntegrationServiceApplication`。聚合服务已经加载integration模块并注册执行器，同时启动会导致9999端口冲突或同一AppName出现非预期节点。

## 4. 关键环境变量

```text
XXL_JOB_ENABLED=true
XXL_JOB_ADMIN_ADDRESSES=http://localhost:9090/xxl-job-admin
XXL_JOB_ACCESS_TOKEN=与Admin一致的Token
XXL_JOB_EXECUTOR_APP_NAME=procurement-integration-executor
XXL_JOB_EXECUTOR_PORT=9999
XXL_JOB_EXECUTOR_ADDRESS=http://host.docker.internal:9999
XXL_JOB_LOG_PATH=./logs/xxl-job
```

Windows宿主机运行Java、Admin运行在Docker时，Admin必须通过`host.docker.internal`回调执行器。生产环境改成容器或内网可达地址，不能照搬本机地址。

## 5. 两层状态如何理解

XXL-JOB负责“什么时候触发、选择哪个实例、是否超时和是否重试”。SRM负责“业务数据是否正确、当前成功检查点在哪里、旧实例是否还有写权限”。

Handler同步等待SRM业务运行结束后才返回：

```text
Admin触发 -> Handler创建job_run -> 获取resource lease -> 分页同步
          -> SRM提交终态 -> Handler向Admin回报成功或失败
```

因此Admin显示成功时，对应SRM运行已经成功；不会出现“只进入本地队列就向Admin报成功”的假成功。

## 6. 重试与补偿

- `trigger_type=XXL`：业务失败直接进入`FAILED`，Handler向Admin回报失败，由XXL-JOB创建后续调度尝试；
- `trigger_type=MANUAL/RETRY`：可重试错误进入`RETRY_WAIT`，由SRM本地队列按30、60、120、240秒等退避；
- 两个入口共用资源租约，同一数据集不会并发写；
- 新尝试从PostgreSQL最后成功检查点继续，不重新全量扫描；
- 完全相同的XXL回调通过`jobId + SHA-256(jobLogFileName) + shardIndex`复用原运行；
- 不保存执行器绝对日志路径，只保存64位摘要。

## 7. 页面观察点

SRM“主数据同步中心”中检查：

- XXL-JOB是否启用、Admin地址、执行器AppName；
- 两个任务的Handler、Cron和资源租约键；
- 运行来源是否为`XXL`；
- XXL任务ID、日志键摘要、执行节点和分片参数；
- 拉取、应用、忽略、失败页、检查点、对账差异和业务告警。

“业务执行开关”是SRM侧安全开关，不等同于Admin中的Cron启停。Cron修改和启停必须在XXL-JOB Admin操作。

## 8. 常见问题

| 现象 | 检查项 |
| --- | --- |
| Admin 502或打不开 | 9090映射、MySQL健康状态、Admin容器日志 |
| 执行器离线 | 9999端口、注册地址、`host.docker.internal`、AccessToken |
| 9999端口占用 | 是否同时启动聚合服务和独立integration-service |
| Token mismatch | Admin与Java进程的`XXL_JOB_ACCESS_TOKEN`是否完全一致 |
| 种子任务不存在 | 是否复用了旧MySQL数据卷；初始化脚本只在新卷执行 |
| `JOB_BUSY` | 同数据集存在人工或XXL活动运行，等待结束或检查失联租约 |
| 页面无XXL字段 | 历史运行允许为空；确认新运行的`triggerType`为`XXL` |

## 9. 验证边界

`scripts/verify-xxl-job-scheduling.ps1`只检查现有服务与SRM运行信息，不负责启动或停止任何进程。实际Handler触发、瞬时故障注入、双节点故障转移和数据库迁移验证均需单独获得许可，并如实记录未执行项。
