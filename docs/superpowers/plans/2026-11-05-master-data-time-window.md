# 主数据组合游标改造实施计划

> For agentic workers: use superpowers:subagent-driven-development or superpowers:executing-plans. 本任务按明确的文件边界分工，变更共享 DTO 后再接入各层。

**Goal:** 将同步引擎、模拟源和页面统一为 TIME_WINDOW_V2，并重建指定同步 schema。
**Architecture:** 源端一张 sap_master_data；HTTP 提取；SRM 保存固定窗口与组合游标，页事务提交、幂等时间保护、独立对账修复。复用 XXL 与租约。
**Tech Stack:** Java17/Spring Boot/MyBatis/PostgreSQL/Vue3。
**Spec:** resource/项目发开文档/数据同步功能开发文档/修改时间与主键组合游标-需求与详细设计.md。

## 约束与授权

- 用户已授权清空 integration_schema、mock_sap_schema 并初始化演示数据，其他 schema 保留。
- 构建/测试/联调授权尚待答复，不主动运行。
- 使用 codex/master-data-time-window 分支；保留用户已有文档修改和暂存区，不提交或覆盖。
- 不改历史 V1/V2/V3 迁移，新增目标 V4 与源 V2；最终源端只有一张业务表（Flyway 历史表属技术元数据）。
- 源时间不等于提交时间，L=120秒、R=300秒初值，超边界以全量对账兜底。

## 共享接口契约

- CostCenterDTO: 原业务字段＋blocked/enabled/isDeleted；ExchangeRateDTO: 原汇率字段＋blocked/enabled/isDeleted。
- SourceRecordDTO(entityType,businessKey,long sourceId,Instant updatedAt,costCenter,exchangeRate)，ID 按 JSON 字符串输出。
- SourceCursorDTO(Instant updatedAt,long sourceId)，SourceAnchorRespDTO(Instant sourceNow,long maxSourceId)。
- SourcePageRespDTO(List<SourceRecordDTO> items,SourceCursorDTO nextCursor,boolean hasMore)。
- 维护请求 DTO(data,Long sourceId,Instant expectedUpdatedAt)：新增后二者为空，编辑必须携带源 ID 与精确时间。
- SapMasterDataClient: anchor(type), fullPage(type,afterId,maxId,size), changes(type,start,end,cursor,size), find(type,id)；find 仅 404 返回 null。
- SyncCheckpoint: mode/generation/baselineComplete/baselineAnchorAt/baselineMaxSourceId/baselineLastSourceId/completedWatermark/activeWindowId/windowStartAt/windowEndAt/pageCursor/sourceTimeHighwater/lastPageAt/pageSequence。
- 管理 HTTP 路径保留，checkpoint 响应改为上述字段；job_definition.committedCursor 为相同 JSON。overview 增加 settleDelaySeconds/overlapSeconds。

## 任务

- [ ] Task 1: 共享 DTO、codec、checkpoint、配置和有意义的边界测试；测试执行以授权为前提。
- [ ] Task 2: 模拟源单表、源 V2 迁移、Mapper/Service/Controller、HTTP client；源状态独立，编辑时间条件更新，故障开关进程内存。
- [ ] Task 3: 目标 V4 迁移、Mapper/实体、页原子事务、组合游标引擎、完整水位、重试恢复、对账扫描与复核修复；现有 Job/Lease 保持语义。
- [ ] Task 4: Vue/types/API 改造：展示窗口、水位、源ID/时间、独立状态、编辑冲突和修复差异。
- [ ] Task 5: 数据库重建脚本，先只读检查跨 schema 依赖及现有进程；限定 schema，记录实际执行结果。
- [ ] Task 6: 授权后执行必要构建/测试/接口和页面验证；最终代码审阅并修复，更新指南与简历完成状态。

## Review Focus

- 同时间跨页不能遗漏，边界采用[S,E)，时间微秒不截断，大ID字符串。
- 第2条写入失败，业务/进度/统计整页回滚；最后页完成标记与水位共提交。
- 旧token及取消状态不得提交，长期中断恢复原窗口，源时间回退告警。
- 只读对账不改业务或AUTO水位，失败扫描不产生缺失结论；修复不覆盖较新目标。
- 正常删除为软删，源端含不可用行；物理删除点查复核，不自动删除本地历史引用。
