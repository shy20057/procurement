# integration-service 微服务架构说明

> 本文档从宏观层面讲解 `integration-service` 微服务的项目结构与各包职责。
> 服务所属系统:`procurement-backend` 采购后端。

## 一、整体定位

`integration-service`(集成服务)是采购系统中的一个独立微服务,核心职责是:

**把 SAP 系统里的主数据(成本中心、汇率)同步到采购业务数据库**。

围绕这一职责,它还附带了一整套完整机制:

- 任务调度(本地 `@Scheduled` + XXL-Job 分布式调度)
- 分布式锁(lease 租约机制,防止多节点重复执行)
- 同步断点(checkpoint)与对账(reconciliation)
- 分页审计(audit)、失败记录、告警(alert)
- 一套用于本地测试的「Mock SAP」

### 基本信息

| 项目 | 值 |
| --- | --- |
| 服务名 | `integration-service` |
| 端口 | `8086` |
| 数据库 | PostgreSQL(schema: `integration_schema`) |
| 技术栈 | Spring Boot 3 · MyBatis-Plus · Flyway · RabbitMQ · Nacos · XXL-Job |
| 同步主数据类型 | `CostCenter`(成本中心)、`ExchangeRate`(汇率),统一抽象为 `MasterDataType` 枚举 |

### 依赖清单(pom.xml)

- `spring-boot-starter-web` — Web/REST
- `spring-boot-starter-jdbc` + `mybatis-plus-spring-boot3-starter` — 持久层
- `postgresql` — 数据库驱动
- `flyway-core` — 数据库迁移
- `spring-boot-starter-validation` — 参数校验
- `spring-boot-starter-amqp` — RabbitMQ
- `spring-boot-starter-actuator` — 监控
- `spring-cloud-starter-alibaba-nacos-discovery` — 服务注册发现
- `xxl-job-core` — 分布式任务调度

---

## 二、包结构总览

源码根包:`com.example.procurement.integration`

```
integration
├── client           外部系统客户端(SAP)
├── config           配置装配
├── controller       对外 HTTP API(含 mock)
├── dao              持久层(entity / mapper / typehandler)
├── domain           领域模型(context / enums / result)
├── dto              传输对象(req / resp / domain)
├── exception        异常与全局处理
├── job              任务调度与执行(核心)
├── mock             Mock SAP(本地假上游)
├── security         访问控制
├── service          业务服务层(接口 + impl)
├── support          支撑/工具
└── IntegrationServiceApplication   启动类
```

---

## 三、各包详细职责

### 1. `client/` — 外部系统客户端

- `SapMasterDataClient`:调用 SAP 主数据接口的 HTTP 客户端,是本服务唯一「出网」去拿上游数据的入口。

### 2. `config/` — 配置装配

| 类 | 职责 |
| --- | --- |
| `IntegrationMybatisConfiguration` | 本服务的 MyBatis 数据源配置 |
| `MockSapConfiguration` | Mock SAP 的开关/配置 |
| `SyncInfrastructure` / `SyncSettings` | 同步基础设施(线程池、信号量)与同步参数(page-size、batch-size、lease-seconds 等,对应 `application.yml` 的 `integration.sync.*`) |
| `XxlJobConfiguration` / `XxlJobProperties` | XXL-Job 执行器装配 |

### 3. `controller/` — HTTP 入口(对外 API)

- `IntegrationController`:核心集成操作的 REST 接口(触发同步、查询等)
- `IntegrationStatusController`:服务健康/状态查询
- `mock/MockSapController`:暴露一个「假 SAP」的 HTTP 接口,方便联调

### 4. `dao/` — 持久层

**`entity/`** — 数据库表对应 DO 对象,可看出同步链路涉及的表:

- 业务数据:`CostCenterDO`、`ExchangeRateDO`
- 任务模型:`JobDefinitionDO`(任务定义)、`JobRunDO`(任务运行记录)、`JobRequestKeyDO`(任务幂等键)
- 同步状态:`SyncCheckpointDO`(断点/游标)、`SyncDifferenceDO`(差异)、`SyncPageAuditDO` / `SyncPageFailureDO`(分页审计/失败)、`SyncAlertDO`(告警)

**`mapper/`** — MyBatis-Plus Mapper 接口,对应 `resources/mapper/integration/*.xml`。

**`typehandler/`** — `UuidTypeHandler`:UUID 字段类型处理器。

### 5. `domain/` — 领域模型(纯业务对象,不依赖框架)

- **`context/`**:`JobContext`(任务运行上下文)、`SyncCheckpoint`(同步断点)、`XxlTriggerMetadata`(XXL 触发元数据)
- **`enums/`**:`MasterDataType`(主数据类型)、`RetryOwner`(重试归属方)、`TriggerType`(触发方式)
- **`result/`**:`JobExecutionResult`(任务执行结果)、`XxlSubmission`(提交给 XXL 的结果)

### 6. `dto/` — 传输对象(分层清晰)

- **`req/`**:请求入参(如 `SyncRunReqDTO`、`CostCenterMutationReqDTO`、`EnableReqDTO`)
- **`resp/`**:响应出参(如 `PageRespDTO`、`EnvelopeRespDTO`、`JobRunRespDTO`)
- **`domain/`**:内部流转用 DTO(如 `ClaimableRunDTO`、`LeaseStateDTO`、`SourceRecordDTO`)

### 7. `exception/` — 异常与统一处理

- `IntegrationProblem`:领域异常
- `IntegrationExceptionHandler`:全局异常处理器(统一错误响应)

### 8. `job/` — 任务调度与执行(核心)

- `SyncJobExecutor`:**本地调度器**,基于 `@Scheduled` 的多个定时任务驱动任务流转。用 `Semaphore(2)` 限流,通过 `SyncJobService.claim()` 领取任务再执行。
- `XxlMasterDataJobHandlers`:XXL-Job 的任务处理器(与 XXL 调度中心对接)。
- `XxlRuntimeContext` / `XxlJobHelperRuntimeContext`:XXL 运行上下文适配。

### 9. `mock/` — Mock SAP(本地假上游)

- `dao/entity` + `dao/mapper`:Mock 自己的表(`MockMasterRecordDO`、`MockChangeDO`、`MockSnapshotDO`)
- `infrastructure/MockSapDatabase`:内存/库模拟 SAP 数据
- `service` / `service/impl`:Mock 的 SAP 服务实现

### 10. `security/` — 访问控制

- `IntegrationAccess`、`MockSapAccess`:接口鉴权/访问控制。

### 11. `service/` — 业务服务层(最大的一层)

接口 + `impl/` 实现:

| 服务 | 职责 |
| --- | --- |
| `MasterDataSyncService` | 主数据同步主流程 |
| `MasterDataPersistenceService` | 同步数据的落库 |
| `JobExecutionCoordinator` | 任务执行协调器(串起领取 → 执行 → 落库 → 记账) |
| `SyncJobService` | 任务的定义 / 领取 / 状态管理 |
| `SyncLeaseService` | **租约(lease)机制**,分布式锁,防止多节点重复执行 |
| `SyncAlertService` | 告警 |
| `SyncManagementService` | 管理类操作(启停等) |
| `IntegrationQueryService` | 集成数据查询 |

### 12. `support/` — 支撑/工具

- `MasterDataAssembler`:主数据装配
- `MasterDataCodec`:主数据编解码(SAP 报文 ↔ 内部对象)
- `ResponseAssembler`:响应统一装配
- `XxlExecutionIdentity`:XXL 执行身份标识

---

## 四、资源目录(resources/)

| 路径 | 说明 |
| --- | --- |
| `mapper/integration/*.xml` | 本服务 10 张表的 MyBatis SQL |
| `mock-sap-mapper/MockSapMapper.xml` | Mock 表的 SQL |
| `db/integration/migration` | 真实库的 Flyway 迁移脚本 |
| `db/mocksap/migration` | Mock 库的 Flyway 迁移脚本 |
| `application.yml` / `application-dev.yml` | 配置 |

---

## 五、数据流转总结

一句话串起整个链路:

```
SAP(或 Mock SAP)
    │
    ▼
client(SapMasterDataClient)  拉取上游主数据
    │
    ▼
service(MasterDataSyncService)  同步 / 对账 / 落库
    │
    ▼
dao  持久化(CostCenter / ExchangeRate + 各类同步状态表)
    │
    ▲
job  驱动(SyncJobExecutor 本地调度 + XXL-Job 分布式调度)
    │
    ▲
lease(SyncLeaseService)  分布式锁,保证多节点下不重复执行

执行结果写入:JobRun / Checkpoint / Difference / Audit
异常处理:走 SyncAlertService 告警
对外暴露:controller 提供触发与查询接口
```

---

## 六、最值得深入的两块

1. **任务领取与执行链路**:`SyncJobExecutor` 如何通过 `claim()` + `Semaphore` + `lease` 完成分布式下的任务领取与并发控制。
2. **同步断点与对账**:`SyncCheckpoint`(断点续传)与 `SyncReconciliationMapper`(对账差异)的设计。

如需继续深入,可针对以上模块单独讲解。
