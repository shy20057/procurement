# PTP模块接口清单

版本：V1.0　日期：2026-09-17

> 与《08-PTP技术方案设计文档.md》《09-PTP数据模型设计.md》配套。路径为API网关侧路径（`/api/ptp/**` → `ptp-service`）。标记 **[远程调用·SRM]** 内部经Feign调用srm-service；标记 **[远程调用·STC]** 内部经Feign调用stc-service；标记 **[跨服务写]** 是本项目中唯一需要联动修改STC数据的调用，实现时参照08号文档第7章的一致性处理方案。

---

## 1. 采购申请单（D17） `/api/ptp/requisitions`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/requisitions | status, applicant, pageNo, pageSize | list[bill_no, applicant, status, apply_date] | |
| 详情查询 | GET | /api/ptp/requisitions/{id} | id | 单头 + entries[申请明细分录] | |
| 新增 | POST | /api/ptp/requisitions | applicant, department, expect_delivery_date, budget_code, entries[] | id, bill_no | |
| 编辑 | PUT | /api/ptp/requisitions/{id} | 同新增 | - | 仅DRAFT/PRICED可编辑 |
| **提交取价** [远程调用·STC] | POST | /api/ptp/requisitions/{id}/price | id | entries[已填充unit_price, price_source, price_source_bill_no] | 逐行按"BPA→价目表→人工"优先级查询STC接口取价；status→PRICED |
| 提交审批 | POST | /api/ptp/requisitions/{id}/submit | id | - | status→SUBMITTED |
| 审核 | POST | /api/ptp/requisitions/{id}/approve | id | - | status→APPROVED |
| 驳回 | POST | /api/ptp/requisitions/{id}/reject | id, reject_reason | - | status→REJECTED |
| **下推生成采购订单** | POST | /api/ptp/requisitions/{id}/push-to-order | id, entries_selected[] | 新建D18的 id, bill_no | 对应方案P1，可选择部分行下推（分批下单） |

---

## 2. 采购订单（D18） `/api/ptp/orders`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/orders | status, supplier_code, price_source_type, pageNo, pageSize | list[bill_no, supplier_code, total_amount, status] | |
| 详情查询 | GET | /api/ptp/orders/{id} | id | 单头 + entries[订单明细分录] | |
| 新增 | POST | /api/ptp/orders | source_pr_bill_no, supplier_code, price_source_type, price_source_bill_no, entries[] | id, bill_no | 通常由P1下推 |
| 编辑 | PUT | /api/ptp/orders/{id} | 同新增 | - | 仅DRAFT可编辑 |
| **提交** [远程调用·SRM+STC·强校验] | POST | /api/ptp/orders/{id}/submit | id | - | ①同步查询SRM确认supplier_code非FROZEN/BLACKLIST；②若price_source_type=CONTRACT，查询STC合同状态有效；③若=BPA，查询STC框架协议额度充足；任一校验失败则拒绝提交，返回具体原因 |
| **审核通过** [跨服务写] | POST | /api/ptp/orders/{id}/approve | id | - | 若price_source_type=BPA：调用 `POST /api/stc/bpas/{bpaId}/accumulate-usage` 累加额度（Q2），**该调用失败则本次审核整体回滚**；status→CONFIRMED |
| 供应商协同确认 | POST | /api/ptp/orders/{id}/confirm | id | - | 模拟供应商门户确认动作 |
| 收货进度查询 | GET | /api/ptp/orders/{id}/receive-progress | id | entries[item_code, quantity, received_quantity, receive_status] | |
| 关闭 | POST | /api/ptp/orders/{id}/close | id | - | status→CLOSED |

---

## 3. 采购收货单（D19） `/api/ptp/goods-receipts`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/goods-receipts | status, source_po_bill_no, pageNo, pageSize | list[bill_no, source_po_bill_no, status] | |
| 详情查询 | GET | /api/ptp/goods-receipts/{id} | id | 单头 + entries[收货明细分录] | |
| 新增 | POST | /api/ptp/goods-receipts | source_po_bill_no, supplier_code, receiver, entries[] | id, bill_no | |
| 编辑 | PUT | /api/ptp/goods-receipts/{id} | 同新增 | - | 仅DRAFT可编辑 |
| **确认** | POST | /api/ptp/goods-receipts/{id}/confirm | id | - | status→CONFIRMED；触发Q1反写D18的received_quantity与receive_status |
| **下推生成入库单** | POST | /api/ptp/goods-receipts/{id}/push-to-stock-in | id | 新建D20的 id, bill_no | 对应P3 |
| **下推生成验收单** | POST | /api/ptp/goods-receipts/{id}/push-to-acceptance | id | 新建D21的 id, bill_no | 对应P4，与push-to-stock-in互不依赖，可分别调用 |

---

## 4. 采购入库单（D20） `/api/ptp/stock-in`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/stock-in | status, warehouse_code, pageNo, pageSize | list[bill_no, source_gr_bill_no, status] | |
| 详情查询 | GET | /api/ptp/stock-in/{id} | id | 单头 + entries[入库明细分录] | |
| 新增 | POST | /api/ptp/stock-in | source_gr_bill_no, warehouse_code, entries[] | id, bill_no | 通常由P3下推 |
| 编辑 | PUT | /api/ptp/stock-in/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 确认 | POST | /api/ptp/stock-in/{id}/confirm | id | - | status→CONFIRMED |

---

## 5. 采购验收单（D21） `/api/ptp/acceptances`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/acceptances | status, conclusion, pageNo, pageSize | list[bill_no, source_gr_bill_no, conclusion, status] | **可被SRM服务反向查询调用**（人工创建绩效考核单时引用，见11号文档） |
| 详情查询 | GET | /api/ptp/acceptances/{id} | id | 单头 + entries[验收明细分录] | 同上 |
| 新增 | POST | /api/ptp/acceptances | source_gr_bill_no, inspector, entries[] | id, bill_no | 通常由P4下推 |
| 编辑 | PUT | /api/ptp/acceptances/{id} | 同新增 + conclusion | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/ptp/acceptances/{id}/submit | id | - | status→SUBMITTED |
| 审核 | POST | /api/ptp/acceptances/{id}/approve | id | - | status→APPROVED |

---

## 6. 对账单（D22） `/api/ptp/reconciliations`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/reconciliations | status, match_status, supplier_code, pageNo, pageSize | list[bill_no, supplier_code, period, match_status] | |
| 详情查询 | GET | /api/ptp/reconciliations/{id} | id | 单头 + entries[对账明细分录] | |
| **自动生成对账单** | POST | /api/ptp/reconciliations/generate | supplier_code, period | id, bill_no, entries[自动汇总] | 按supplier_code+period汇总关联的收货/验收/发票数据生成草稿，diff_amount超阈值自动标记match_status=DIFF |
| 编辑 | PUT | /api/ptp/reconciliations/{id} | entries[] + diff_reason | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/ptp/reconciliations/{id}/submit | id | - | |
| 确认 | POST | /api/ptp/reconciliations/{id}/confirm | id | - | status→CONFIRMED；DIFF状态需填写diff_reason才允许确认 |
| **下推生成应付结算单** | POST | /api/ptp/reconciliations/{id}/push-to-settlement | id | 新建D24的 id, bill_no | 对应P5，与push-to-monthly-settlement互斥 |
| **下推生成月结单** | POST | /api/ptp/reconciliations/{id}/push-to-monthly-settlement | id, settle_period | 新建/更新D25的 id, bill_no | 对应P6，若当期月结单已存在则追加汇总分录而非新建 |

---

## 7. 发票登记单（D23） `/api/ptp/invoices`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/invoices | status, verify_status, supplier_code, pageNo, pageSize | list[bill_no, invoice_no, invoice_amount, verify_status] | |
| 详情查询 | GET | /api/ptp/invoices/{id} | id | 单头 + entries[发票明细分录] | |
| 新增 | POST | /api/ptp/invoices | supplier_code, invoice_no, invoice_date, invoice_type, entries[] | id, bill_no | |
| 编辑 | PUT | /api/ptp/invoices/{id} | 同新增 | - | 仅DRAFT可编辑 |
| **发起税务验真**（预留，Mock） | POST | /api/ptp/invoices/{id}/verify | id | verify_status | 本阶段Mock固定返回PASSED，真实场景对接BTS |
| 提交 | POST | /api/ptp/invoices/{id}/submit | id | - | |
| 确认 | POST | /api/ptp/invoices/{id}/confirm | id | - | status→CONFIRMED |

---

## 8. 应付结算单（D24） `/api/ptp/settlements`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/settlements | status, supplier_code, pageNo, pageSize | list[bill_no, supplier_code, settle_amount, status] | |
| 详情查询 | GET | /api/ptp/settlements/{id} | id | 单头 + entries[结算明细分录] | |
| 编辑 | PUT | /api/ptp/settlements/{id} | payment_terms | - | 通常由P5下推生成，仅可编辑账期条款 |
| 确认 | POST | /api/ptp/settlements/{id}/confirm | id | - | status→CONFIRMED |
| **下推生成请款单** | POST | /api/ptp/settlements/{id}/push-to-payment | id | 新建D26的 id, bill_no | 对应P7 |

---

## 9. 供应商月结单（D25） `/api/ptp/monthly-settlements`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/monthly-settlements | status, supplier_code, settle_period, pageNo, pageSize | list[bill_no, supplier_code, settle_period, total_amount] | |
| 详情查询 | GET | /api/ptp/monthly-settlements/{id} | id | 单头 + entries[月结汇总分录] | |
| 确认 | POST | /api/ptp/monthly-settlements/{id}/confirm | id | - | status→CONFIRMED |
| **下推生成请款单** | POST | /api/ptp/monthly-settlements/{id}/push-to-payment | id | 新建D26的 id, bill_no | 对应P8 |

---

## 10. 请款单（D26） `/api/ptp/payment-requests`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/ptp/payment-requests | status, source_type, supplier_code, pageNo, pageSize | list[bill_no, supplier_code, request_amount, status] | |
| 详情查询 | GET | /api/ptp/payment-requests/{id} | id | 单头 + entries[请款明细分录] | |
| 新增（NO_PO场景） | POST | /api/ptp/payment-requests | supplier_code, source_type=NO_PO, source_ecs_bill_no, request_amount, entries[] | id, bill_no | source_ecs_bill_no**预留字段**，本阶段允许手工填写，不做真实ECS校验 |
| 编辑 | PUT | /api/ptp/payment-requests/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/ptp/payment-requests/{id}/submit | id | - | status→SUBMITTED |
| 审核 | POST | /api/ptp/payment-requests/{id}/approve | id | - | status→APPROVED |
| **模拟支付完成**（预留，Mock TMS回调） | POST | /api/ptp/payment-requests/{id}/mock-pay | id | payment_result | status→PAID；触发Q3反写来源结算/月结单的paid_flag；真实场景由TMS异步回调触发，本阶段用人工按钮模拟 |

---

## 11. 与SRM/STC接口清单的错误码延用

沿用《03-SRM接口清单.md》《06-STC接口清单.md》第9/10章错误码表 + 《00-技术架构与技术栈说明.md》第7章微服务专属错误码段，另补充PTP专属：

| 错误码 | 说明 |
|---|---|
| 40905 | 供应商资格校验未通过（FROZEN/BLACKLIST），拒绝提交采购订单 |
| 40906 | STC合同/价目表/框架协议状态无效或已过期 |
| 40907 | 框架协议额度不足，拒绝提交采购订单 |
| 50104 | 跨服务写操作失败（BPA额度累加调用失败），订单审核整体回滚 |

---

## 12. 与前端页面的对应关系（简要）

| 页面 | 对应资源 | 说明 |
|---|---|---|
| 采购申请列表/表单 | /api/ptp/requisitions | 需展示"提交取价"按钮及取价来源角标 |
| 采购订单列表/表单 | /api/ptp/orders | 提交/审核按钮需展示远程校验loading态及失败具体原因（供应商冻结/合同过期/额度不足） |
| 采购收货列表/表单 | /api/ptp/goods-receipts | |
| 采购入库列表/表单 | /api/ptp/stock-in | |
| 采购验收列表/表单 | /api/ptp/acceptances | |
| 对账列表/表单 | /api/ptp/reconciliations | 需展示"自动生成对账单"入口，DIFF行高亮提示 |
| 发票登记列表/表单 | /api/ptp/invoices | |
| 应付结算列表/表单 | /api/ptp/settlements | |
| 供应商月结列表/表单 | /api/ptp/monthly-settlements | |
| 请款列表/表单 | /api/ptp/payment-requests | 需展示"模拟支付完成"按钮（demo专用） |
