# 后端双启动模式

## 开发：聚合装配

`aggregation-service` 依赖各业务模块，在一个 Spring Boot 进程中加载现有 Controller、Service 以及 SRM、STC Mapper。它监听`8090`，并内嵌一个XXL-JOB执行器监听`9999`。PostgreSQL、Redis、RabbitMQ、XXL-JOB MySQL与Admin仍由容器提供。

在项目根目录运行 `scripts/run-monolith.ps1 -JavaHome "JDK 17 目录"`。脚本会读取 `.env`、启动所需容器、构建聚合服务并启动它。只需要后端时加 `-SkipFrontendBuild`。聚合服务的 `dev` 配置连接 `procurement` 数据库，并通过独立的 Flyway 迁移记录分别初始化 `srm_schema` 和 `stc_schema`。如果从 IDEA 启动，需在运行配置中提供与 `.env` 一致的数据库和消息中间件环境变量。

需要 8080 入口时，再启动 `api-gateway`。它默认使用 `aggregation` profile，将业务请求转发到 `http://localhost:8090`；开发环境无需分别启动领域服务。

数据同步开发环境的手动进程只有：

```text
Vue 5173 -> Gateway 8080 -> Aggregation 8090 / XXL Executor 9999
XXL-JOB Admin 9090 -> host.docker.internal:9999
```

不要同时启动`integration-service`。它会使用相同AppName注册第二个执行器，并与聚合服务争用9999端口。前端始终请求`/api`，不会因启动模式切换而改地址。

## 独立微服务

分别启动业务服务和`api-gateway`，并为网关设置`SPRING_PROFILES_ACTIVE=microservices`。线上不启动聚合服务。多个`integration-service`实例使用相同`XXL_JOB_EXECUTOR_APP_NAME`注册到Admin，实例地址由部署环境显式配置；前端仍通过网关使用相同API路径。

当前仓库的 SRM、STC 接口已经有业务实现，采购和结算模块仍主要提供状态接口。部署方式切换不会自动补齐尚未实现的业务接口。
