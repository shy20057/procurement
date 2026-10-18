# XXL-JOB调度与失败补偿验证记录

> 验证日期：2026-10-18（Asia/Shanghai）  
> 验证范围：用户已批准的单元测试、编译、前端测试/构建及静态配置检查。  
> 未授权范围：启动Docker或应用、执行Flyway迁移、写入演示数据、运行时联调及压测。

## 1. 结论

本轮静态验证通过。XXL-JOB执行器配置、执行身份、业务调度归属、资源租约、同步执行协调、两个Handler、前端展示工具均通过自动化测试；聚合服务及其依赖模块完成打包；前端完成类型检查和生产构建；Docker Compose与PowerShell验证脚本通过语法解析。

运行时注册、Admin手动触发、MySQL/PostgreSQL真实迁移、故障注入、双实例故障转移和浏览器联调尚未执行，因此不声明这些场景已经通过。

## 2. 已执行证据

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量单元测试 | `mvn.cmd -pl integration-service -am test` | 20个测试通过，0失败，0错误 |
| 后端聚合打包 | `mvn.cmd -pl aggregation-service -am -DskipTests package` | 8个Reactor模块全部成功 |
| 前端单元测试 | `npm.cmd test -- --run` | 2个测试文件、3个测试通过 |
| 前端生产构建 | `npm.cmd run build` | `vue-tsc`与Vite构建成功 |
| Compose静态解析 | `docker compose --env-file .env -f deploy/docker-compose.yml config --quiet` | 退出码0；未启动容器 |
| PowerShell脚本语法 | PowerShell AST Parser解析`scripts/verify-xxl-job-scheduling.ps1` | 通过 |

## 3. 回归问题与修复

定向回归先复现了4个边界问题，修复后9个定向测试全部通过：

1. 同一人工幂等键在业务开关关闭后重放时，仍返回原运行，不错误创建任务或抛`JOB_DISABLED`；
2. 本地调度器遇到第一个繁忙数据集时，会继续尝试候选任务，避免其他数据集饥饿；
3. 旧运行的owner或fence token已被新实例替换时，即使当前租约仍有效，也能识别旧运行失联；
4. XXL运行提交后、抢占前业务开关被关闭时，Handler返回已持久化的`JOB_DISABLED`，不覆盖成`JOB_BUSY`。

同时固定XXL-JOB初始化库名为官方脚本使用的`xxl_job`，并使PostgreSQL任务定义中的展示Cron与XXL-JOB六段Cron保持一致。

## 4. 已知非阻塞警告

- 当前Node.js为20.15.0，Vite建议20.19+或22.12+；本次构建仍成功。项目README建议使用Node.js 22.18+或24.12+。
- `vite.config.ts`仍使用`__dirname`，Vite提示未来native配置加载器兼容性警告；不是本次功能引入的失败。
- 主入口压缩后约870 kB，Vite提示可继续做代码分包；不影响本次XXL-JOB页面构建。
- Maven资源复制使用Windows平台编码GBK，构建成功，但后续可统一显式UTF-8以提高跨平台稳定性。

## 5. 尚待单独批准的运行时验证

- 创建或更新XXL-JOB MySQL数据卷并验证两份初始化SQL；
- 启动XXL-JOB Admin、聚合服务、网关和前端；
- 确认`procurement-integration-executor`唯一注册及两个Handler可发现；
- 在Admin启用并触发成本中心任务，核对XXL日志与SRM `job_run`一一对应；
- 注入SAP 503，验证SRM端XXL运行保持`FAILED`且只由XXL重试；
- 并发触发人工与XXL运行，验证`SAP_COST_CENTER_WRITER`互斥和fencing；
- 经单独批准后使用第二执行器验证FAILOVER与旧实例写隔离。
