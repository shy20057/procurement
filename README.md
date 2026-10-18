# Procurement Lab：供应商评估与采购协同

个人复刻型企业采购项目，覆盖 SRM（供应商管理）、STC（寻源定标）和 PTP（采购到付款），用于实践 Java 17、Vue 3、微服务、RabbitMQ、Redis、Elasticsearch 与可靠消息。

## 技术基线

- 后端：Java 17、Spring Boot 3.0.7、Spring Cloud 2022.0.3、Maven
- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus
- 数据：PostgreSQL 16（业务真相源）、MySQL 8（XXL-JOB调度元数据）、Redis（缓存/幂等/限流）、Elasticsearch 9.5.3（搜索索引）
- 集成：RabbitMQ 4.3、Transactional Outbox、MinIO
- 质量：JUnit 5、Testcontainers、Vitest、Playwright、OpenAPI 3.1、AsyncAPI 3.0

Java 17 是强制运行基线；不要使用更高版本独有语法。

## 服务

| 服务 | 职责 | 默认端口 |
|---|---|---:|
| aggregation-service | 开发环境的模块化单体装配，托管Vue页面 | 8090 |
| web | Vue 3 管理端与供应商门户 | 5173 |
| api-gateway | 鉴权、路由、限流、关联ID | 8080 |
| identity-service | 用户、组织、角色、权限 | 8081 |
| supplier-service | 注册、准入、档案、敏感变更、绩效 | 8082 |
| sourcing-service | RFQ、报价、评标、定标、协议价 | 8083 |
| procurement-service | 需求、PR、PO、收货、服务验收 | 8084 |
| settlement-service | 发票、匹配、付款申请 | 8085 |
| integration-service | BMS/BTS/TMS/SAP/ECS 模拟适配 | 8086 |

每个服务独立 schema/数据库账号，禁止跨服务直连数据库。个人开发期可共用一个 PostgreSQL 实例。

## 主演示链路

供应商注册 → 准入 → 非目录需求 → RFQ → 报价评审 → 定标 → PR/预算 → PO → 物料收货或服务验收 → 发票核验与匹配 → 付款 → 绩效回流。

## 当前实现状态

已完成可运行基线和单体模式下的采购闭环 MVP：

- Maven多模块及7个Spring Boot微服务
- Spring Cloud Gateway路由、CORS与关联ID传播
- Vue 3管理端框架和供应商准入页面
- 供应商注册、USCC唯一校验、分页查询、准入审批和乐观锁
- “采购一单到底”页面，可从需求依次推进RFQ、报价、定标、PR、PO、收货、发票匹配和付款
- PostgreSQL持久化采购流程状态、金额快照及各阶段业务单号，禁止跳步并使用乐观锁控制并发
- Flyway初始化演示供应商`SUP-DEMO-001`，启动后可直接创建第一张采购需求单
- PostgreSQL/Flyway表结构
- Redis供应商详情缓存
- 同事务Outbox、RabbitMQ Exchange/Queue和定时可靠投递
- identity、sourcing、procurement、settlement、integration服务边界骨架

当前闭环是便于本地走单的 happy-path MVP：各阶段共享一张流程聚合表，BMS/BTS/TMS行为使用确定性模拟。生产级微服务拆分、多级审批、异常分支、部分收货和付款失败重试仍需后续实现。当前开发凭证仅用于本地：`buyer/demo123`、`manager/demo123`、`auditor/demo123`；身份服务目前返回开发会话，接入非本地环境前必须替换为签名JWT。

## 文档入口

### SAP主数据同步

学习代码先看[集成模块分层与代码阅读指南](resource/项目发开文档/简历五大功能技术实现方案/00-集成模块分层与代码阅读指南.md)。集成模块已统一为Controller → Service/Impl → MyBatis Mapper/XML，数据模型按DO、ReqDTO、RespDTO与Domain上下文区分。

新增入口：顶部“外围系统集成” → “数据同步中心”，路径`/integration/sync`。支持SAP模拟源维护、首次全量与增量拉取、成本中心/汇率本地列表、成功页检查点、运行详情、重试取消、对账修复和告警待办。

当前实现通过HTTP模拟SAP，使用数据库页事务与批量Upsert；不包含真实SAP JCo联通或CDC/Canal。聚合与独立集成启动均支持，首次启动会创建独立的`integration_schema`和开发用`mock_sap_schema`。详见[实现与演示指南](resource/项目发开文档/简历五大功能技术实现方案/01-主数据同步实现与演示指南.md)。

前端单独连接集成服务时，可通过`VITE_DEV_API_PROXY`指定后端地址；默认仍为原网关8080。测试和联调按项目约定先经用户批准。

### XXL-JOB分布式调度

成本中心和汇率的自动业务Cron由XXL-JOB 2.4.2统一管理。Admin默认地址为`http://localhost:9090/xxl-job-admin`，执行器端口为`9999`。XXL-JOB负责Cron、路由、超时和失败重试；PostgreSQL中的SRM任务表继续负责幂等、资源租约、fencing token、分页检查点和业务运行详情。

开发聚合模式只需手动启动`aggregation-service`和`api-gateway`。执行器已经内嵌在聚合服务中，不能同时启动独立`integration-service`，否则会重复注册并冲突占用9999端口。首次创建XXL MySQL数据卷时会初始化两个停用任务；聚合服务注册成功后，在Admin确认配置再启用。详见[XXL-JOB实现与演示指南](resource/项目发开文档/简历五大功能技术实现方案/02-XXL-JOB实现与演示指南.md)。

### 原有项目文档

- [项目范围](docs/01-scope.md)
- [领域模型](docs/02-domain-model.md)
- [总体架构](docs/03-architecture.md)
- [服务边界](docs/04-service-boundaries.md)
- [数据设计](docs/05-data-model.md)
- [API设计](docs/06-api-design.md)
- [事件设计](docs/07-event-design.md)
- [中间件设计](docs/08-middleware-design.md)
- [安全设计](docs/09-security-design.md)
- [测试计划](docs/10-test-plan.md)
- [单体与微服务双启动模式](docs/11-run-modes.md)
- [OpenAPI契约](contracts/openapi/procurement-api.yaml)
- [AsyncAPI契约](contracts/asyncapi/procurement-events.yaml)
- [原始交互流程图](供应商评估与采购协同流程图.html)

## 本地基础设施

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f deploy/docker-compose.yml up -d
docker compose --env-file .env -f deploy/docker-compose.yml ps
```

端口以 `.env` 为准；仓库当前配置使用 PostgreSQL `5433`、XXL-JOB MySQL `3307`、XXL-JOB Admin `9090`、Redis `6381`、RabbitMQ AMQP `5672`/管理台 `15672`。Elasticsearch、Kibana 和 MinIO 仅在需要时启动。

`docker-compose.yml` 仅用于本地学习，关闭了 Elasticsearch TLS，且示例密码不能用于公网或生产环境。

## 启动应用

前置条件：JDK 17、Maven 3.9+、Node.js 22.18+或24.12+、Docker Desktop。

开发环境推荐聚合模式。业务模块、中间件和Vue页面均保留，业务后端只需启动 `aggregation-service` 一个 Java 进程；需要统一入口时再启动网关：

```powershell
.\scripts\run-monolith.ps1 -JavaHome "你的JDK17安装目录"
```

启动后直接访问`http://localhost:8090`。已配置`JDK17_HOME`或Java 17版`JAVA_HOME`时，可以省略`-JavaHome`。

如果需要通过网关 `http://localhost:8080` 访问，再单独启动 `api-gateway`；网关默认使用 `aggregation` profile，路由到 8090。聚合服务同时在9999注册XXL执行器。不要再启动独立`integration-service`。聚合服务启动时由 Flyway分别初始化业务schema。上线后独立启动业务微服务，并为网关指定`microservices` profile。

当前聚合进程会加载仓库中已经实现的 SRM、STC 接口、开发用身份接口，以及其他业务模块现有的接口。采购和结算模块目前主要是状态接口，下面的完整演示链路仍需后续业务实现：

```text
需求 → RFQ → 报价 → 定标 → PR审批 → PO下达 → 收货 → 发票匹配 → 付款完成
```

微服务集成模式使用以下步骤：

```powershell
# 1. 基础设施
Copy-Item .env.example .env
docker compose --env-file .env -f deploy/docker-compose.yml up -d

# 2. 后端（各服务分别在独立终端启动）
Set-Location backend
mvn -DskipTests package
mvn -pl supplier-service spring-boot:run
# 其他终端启动 identity-service、sourcing-service、procurement-service 等
# 网关终端设置 SPRING_PROFILES_ACTIVE=microservices 后启动 api-gateway

# 3. 新终端启动前端
Set-Location web
npm install
npm run dev
```

访问`http://localhost:5173`。前端开发代理将`/api`转发到`http://localhost:8080`。

供应商纵向切片接口：

```text
POST /api/v1/suppliers
GET  /api/v1/suppliers
GET  /api/v1/suppliers/{id}
POST /api/v1/suppliers/{id}:approve
```

## 开工门槛

- [ ] P0 主链路和服务边界已理解
- [ ] OpenAPI、AsyncAPI 可解析
- [ ] Compose 中所有健康检查通过
- [ ] 每项业务数据只有一个权威服务
- [ ] MQ 消费者有幂等键、重试和死信方案
- [ ] Redis/ES 故障不导致业务真相丢失

## 实施顺序

1. 建 Maven 多模块与 Vue 3 工程，仅实现登录和健康检查。
2. 用 PostgreSQL 完成供应商→PR→PO 最小同步闭环。
3. 加 Outbox/RabbitMQ，实现索引和外部集成异步化。
4. 加 Redis 缓存、幂等、限流；确保可降级回数据库。
5. 加 Elasticsearch 搜索与全量重建。
6. 补齐发票匹配、付款、监控和故障测试。
