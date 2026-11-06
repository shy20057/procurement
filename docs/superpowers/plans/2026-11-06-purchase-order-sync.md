# 采购订单同步 Implementation Plan

**Goal:** 将 ERP 已发布的采购订单头、行与取消状态通过 XXL-JOB 同步至现有 SRM 采购业务表。

**Architecture:** Mock ERP 与 HTTP 客户端隔离。订单编排复用现有任务、围栏、审计与告警，使用独立的三字段检查点。目标业务写入与页审计、检查点共用主库事务。

**Tech Stack:** Java 17、Spring Boot、PostgreSQL、Flyway、XXL-JOB。

**Spec:** resource/项目发开文档/数据同步功能开发文档/SAP ERP 采购订单同步至 SRM — 需求与详细设计.md

## Global Constraints

仅同步订单，不新增供应商业务、门户确认、Spring 本地调度或安全延迟。保留旧数据及用户工作区状态。按用户约定，不运行测试、启动应用或执行迁移，除非另行批准。

## Tasks

- [x] ERP 端增加头行表、发布和稳定分页接口、HTTP 客户端与超时分类。
- [x] SRM 端增加订单来源字段和唯一约束，复用采购业务表与供应商只读查询，支持批量写入和对账读取。
- [x] 复用 XXL 运行生命周期，实现断点、页事务、对账与点查修复，限制采购订单仅从 XXL 执行。
- [x] 记录启动与 XXL 配置，静态检查迁移顺序、事务边界和业务隔离。
- [ ] 获得用户批准后执行测试、数据库迁移与端到端验证。

## Review Focus

同时间戳跨页；行变更推进订单头；失败后原窗口续跑；旧版本不能覆盖新版本；停用任务与重复调度；迁移不得清除用户业务数据；源端暂不可达不能触发目标删除。

## 联调入口

- 目标端使用聚合应用，按现有环境配置 PostgreSQL 和 XXL-JOB。先核对 Flyway 历史。若历史 Integration V4 未应用且包含会被清理的数据，启动会报出受影响的表并要求先确认旧数据迁移方案。
- Mock ERP 通过 `integration.mock-sap.enabled=true` 启用，读取 `/internal/mock-sap/purchase-orders/anchor`，用 `POST /internal/mock-sap/purchase-orders/publish` 发布测试订单。发布需要现有管理开关与密钥；如果配置源端密钥，则携带 `X-Mock-Sap-Key`。供应商编码须与现有供应商档案的 `supplier_no` 一致。
- 使用外部 ERP 时配置 `integration.purchase-order.source-base-url` 指向提供 `anchor`、`baseline`、`changes` 和按源 ID 点查的服务；真实 SAP 接口可通过集成适配层满足此契约。默认订单页大小 50，使用 `integration.purchase-order.page-size` 调整。
- 启用 `SAP_PURCHASE_ORDER_DELTA` 业务开关，在 Admin 配置 `sapPurchaseOrderSyncJobHandler`（每 5 分钟）、`sapPurchaseOrderReconcileJobHandler`（低峰对账）和手动 `sapPurchaseOrderRepairJobHandler`（参数为正整数源订单 ID）。在 Admin 配置失败重试、执行超时和告警收件人。
