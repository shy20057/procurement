# Ledger — 2026-11-05-master-data-time-window.md

- 已阅读详设及现有分层代码，确认原引擎为SOURCE_CURSOR。
- 已确认Git仓库当前存在，创建codex/master-data-time-window，原文档暂存/未暂存修改保持不动。
- 用户批准清空两个同步schema并重建；验证问题尚待回答。
- Ruling: 保留历史迁移，新增增量迁移；重建从V1运行到最新，避免修改既有Flyway校验和。
- Ruling: 共享DTO先固定；源端与前端按接口独立分工，核心目标持久化/引擎由主代理完成。
- Task 1-4: 代码和针对性测试准备完成，尚未编译或执行测试。
- Task 5: 完成。2026-11-05执行维护工具，真实Flyway目标V1-V4、源V1-V2成功；源3CC+3FX，本地副本为空待首次同步；其他schema结构元数据一致。
- Backup: artifacts/master-data-backups/20261105-134028-095-b1080d78-db71-4d06-a03e-a4b0df452755。
- Review: 新增业务键冲突锁死表单、XXL缺失确认跨run丢失、NO_SAFE_WINDOW误记完整成功，均已修正并只读复核。
- Ruling: 对账M=max(源当前最大ID,本地已知源最大ID)，避免最高源ID删除或清空源表后漏报。
- Ruling: 使用既有seen/diff表跨run保存有效404链，源再次出现重置观察；比较统一DB时钟。
- Ruling: 沿用committed_cursor JSON承载检查点，增加独立generation列，保持现有Mapper和页面读取方式。
- Task 6: 用户随后批准构建、测试和联调。后端 62 项测试、前端 12 项测试、聚合编译、前端生产构建、31 项真实接口检查及浏览器检查已通过。
- 最终审阅补齐了 AUTO 读到源记录后重置缺失证据，以及修复请求未知结果时重用原请求参数。改动后复跑后端 62 项测试及前端生产构建，均通过。
