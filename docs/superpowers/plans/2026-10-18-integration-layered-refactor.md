# Integration 模块分层重构实施计划

**Goal:** 按用户已确认的 `controller / service / service.impl / dao.entity / dao.mapper / dto.req / dto.resp` 重构两个已实现功能，便于学习与维护。

**Architecture:** Controller 和 XXL Handler 调用 Service 接口；Service 编排业务、校验和事务；MyBatis Mapper/XML 处理持久化。Entity 使用 DO 命名，与 supplier/sourcing 模块一致；请求与响应使用 ReqDTO/RespDTO，外围契约和执行上下文单独组织。模拟 SAP 保持独立数据源和事务，不与 SRM 业务事务混用。

**Tech Stack:** Java 17、Spring Boot 3.0.7、MyBatis-Plus 3.5.5（使用其 MyBatis 集成）、PostgreSQL、XXL-JOB 2.4.2。

## 约束

- 用户已确认结构并要求执行；本计划直接在当前工作区内实施，不重复请求设计确认，不创建 Git 仓库或提交。
- 不改 API 路径、JSON 字段、页面调用约定、Flyway V1/V2/V3 和业务表。
- 保留人工/XXL 重试归属、请求幂等、资源级租约、fencing token、运行状态和对账行为。
- 保留用户新增学习注释，迁移到对应职责实现类，修正类名引用。
- 每页业务数据、检查点、审计必须在同一个 PostgreSQL 事务内提交；锁顺序为 run → lease → 业务数据/检查点。
- 聚合与独立启动均加载 integration Mapper/XML，避免模拟 SAP 的 Mapper 注册进业务数据源。
- 单元测试先适配代码并保留回归断言；按照用户 AGENTS 指令，本次新的测试、构建、应用启动和数据库验证均须单独批准后执行。

## Tasks

- [x] 1. 记录模型和接口契约；将 MasterData 中混合的请求、响应、外围传输模型、类型枚举、检查点拆开。保留 JSON 名称。
- [x] 2. 创建 DO 和 Mapper/XML：任务定义、运行、请求幂等键、租约、检查点、成本中心、汇率、页记录、对账、告警。添加 UUID TypeHandler。
- [x] 3. 拆分业务 Service：任务管理、租约事务保护、主数据分页落库、对账、同步流程、查询看板和执行协调。Controller 不依赖 Mapper/JdbcTemplate。
- [x] 4. 将 SAP 调用迁到 client，访问控制迁到 security，异常迁到 exception。模拟 SAP 拆成 Controller、Service、Mapper，并由 config 独立配置数据源/SqlSession/事务。
- [x] 5. 配置 MyBatis 独立/聚合扫描、类型处理器和唯一 XML 路径；迁移测试引用与关键幂等/租约回归用例。
- [x] 6. 只读检查接口字段、包引用、SQL 与事务边界，更新学习指南和文件映射。用户已批准测试/打包；干净构建31个测试通过，8模块聚合打包通过。运行时验证未执行。

## 获批后的验证命令

```powershell
# backend 目录：目标模块单元测试
mvn.cmd -pl integration-service -am clean test
# backend 目录：聚合装配和编译兼容性
mvn.cmd -pl aggregation-service -am -DskipTests package
```

Mapper XML 解析、UUID 绑定、独立/聚合扫描和事务联接纳入定向测试；真实 PostgreSQL、模拟源隔离、XXL 回调和前端联调另行批准，不以编译通过替代运行时验证。
