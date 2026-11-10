# Integration Service 采购订单同步

采购订单是当前的同步链路。业务表、时间与 ID 组合游标、对账、修复参数和上线步骤见[采购订单同步详设](../../../../../resource/项目发开文档/数据同步功能开发文档/SAP%20ERP%20采购订单同步至%20SRM%20—%20需求与详细设计.md)。

- `XxlPurchaseOrderJobHandlers` 注册同步、只读对账、指定订单修复三个 Handler。
- `SyncJobServiceImpl` 管理运行幂等、租约获取、失联恢复和操作审计。定时及失败重试由 XXL-JOB Admin 配置。
- `PurchaseOrderSyncService` 通过源接口读取整单，固定增量窗口，并在每页事务内提交订单、审计与检查点。
- `PurchaseOrderStore` 批量写入 SRM 业务订单头和行，通过已有供应商档案解析外部编码。
- `MockPurchaseOrderService` 使用独立的模拟 ERP 数据源，以同一读事务组装订单头和行。
- 历史 Flyway 脚本及旧业务数据留存。现有 XXL-JOB Admin 中的旧任务需要停止调度。
