# STC模块接口清单

版本：V1.0　日期：2026-09-17

> 与《04-STC技术方案设计文档.md》《05-STC数据模型设计.md》配套。路径为**API网关侧路径**（前端/AI客户端统一访问网关，网关按 `/api/stc/**` 转发至 stc-service），返回体统一 `{ code, message, data }`，分页统一 `pageNo`/`pageSize`/`total`/`list`，详见《00-技术架构与技术栈说明.md》第7章。
>
> 标记 **[远程调用]** 的接口，其内部实现需通过 OpenFeign 同步调用 srm-service（见00号文档4.1节），标记 **[事件订阅]** 的动作由 RabbitMQ 消息触发、不对外暴露HTTP接口，仅在说明列注明。

---

## 1. 寻源立项单（D9） `/api/stc/sourcing-projects`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/sourcing-projects | status, sourcing_method, pageNo, pageSize | list[bill_no, project_name, sourcing_method, status] | |
| 详情查询 | GET | /api/stc/sourcing-projects/{id} | id | 单头 + entries[候选供应商分录] | |
| 新增 | POST | /api/stc/sourcing-projects | project_name, category_code, budget_amount, sourcing_method, single_source_reason, initiator | id, bill_no | |
| 编辑 | PUT | /api/stc/sourcing-projects/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 删除 | DELETE | /api/stc/sourcing-projects/{id} | id | - | 仅DRAFT可删除 |
| **刷新候选供应商** [远程调用] | POST | /api/stc/sourcing-projects/{id}/refresh-candidates | id, categoryCode | entries[候选供应商分录] | 内部调用 `SrmArchiveClient.listArchives(categoryCode, certStatus=QUALIFIED,TEMP_QUALIFIED)`，覆盖式写入候选供应商分录 |
| 提交 | POST | /api/stc/sourcing-projects/{id}/submit | id | - | |
| 审核 | POST | /api/stc/sourcing-projects/{id}/approve | id | - | status→APPROVED，解锁下推按钮 |
| **下推生成招标单** | POST | /api/stc/sourcing-projects/{id}/push-to-tender | id | 新建D10的 id, bill_no | 对应方案S1，要求sourcing_method=TENDER |
| **下推生成询价单** | POST | /api/stc/sourcing-projects/{id}/push-to-rfq | id | 新建D11的 id, bill_no | 对应方案S2，要求sourcing_method=RFQ/NEGOTIATION |
| **下推生成定标单** | POST | /api/stc/sourcing-projects/{id}/push-to-award | id | 新建D12的 id, bill_no | 对应方案S3，要求sourcing_method=SINGLE_SOURCE且候选供应商分录仅1行 |
| 关闭 | POST | /api/stc/sourcing-projects/{id}/close | id | - | 手工关闭，或由U2反写自动触发 |

---

## 2. 招标单（D10） `/api/stc/tenders`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/tenders | status, tender_type, pageNo, pageSize | list[bill_no, project_name, tender_type, status] | |
| 详情查询 | GET | /api/stc/tenders/{id} | id | 单头 + entries[投标供应商分录, 评标结果分录] | |
| 新增 | POST | /api/stc/tenders | source_bill_no, tender_type, qualification_prereview, evaluation_method, tech_weight, comm_weight | id, bill_no | 通常由S1下推生成，此接口供人工直接创建的场景 |
| 编辑 | PUT | /api/stc/tenders/{id} | 同新增 + 投标供应商分录 | - | status<OPENED可编辑投标供应商分录 |
| 发布公告 | POST | /api/stc/tenders/{id}/announce | id, announce_date, bid_deadline | - | status→ANNOUNCED |
| **开标** [远程调用可选] | POST | /api/stc/tenders/{id}/open | id, bid_open_date | - | status→OPENED；投标供应商分录锁定；可选先对每个供应商做一次资格状态复核（调用SRM） |
| 录入评标结果 | PUT | /api/stc/tenders/{id}/evaluation | id, entries[评标结果分录] | - | status→EVALUATING；后端按tech_weight/comm_weight计算total_score与rank |
| 完成评标 | POST | /api/stc/tenders/{id}/complete-evaluation | id | - | status→AWARDED |
| 废标 | POST | /api/stc/tenders/{id}/cancel | id, cancel_reason | - | status→CANCELLED |
| **下推生成定标单** | POST | /api/stc/tenders/{id}/push-to-award | id | 新建D12的 id, bill_no | 对应方案S4，取评标结果rank=1供应商 |

---

## 3. 询价单（D11） `/api/stc/rfqs`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/rfqs | status, pageNo, pageSize | list[bill_no, project_name, status] | |
| 详情查询 | GET | /api/stc/rfqs/{id} | id | 单头 + entries[报价分录，按round_no分组] | |
| 新增 | POST | /api/stc/rfqs | source_bill_no, max_rounds, deadline | id, bill_no | |
| 编辑 | PUT | /api/stc/rfqs/{id} | max_rounds, deadline | - | 仅DRAFT可编辑单头 |
| 发布询价 | POST | /api/stc/rfqs/{id}/publish | id | - | status→PUBLISHED |
| **追加一轮报价** | POST | /api/stc/rfqs/{id}/quotes | id, entries[{supplier_code, quote_amount}] | round_no(自动生成) | status→QUOTING；round_no=当前最大轮次+1；历史轮次只读 |
| 关闭询价（生成比价结论） | POST | /api/stc/rfqs/{id}/close | id | 比价结论(最低价供应商建议) | status→CLOSED/COMPARING |
| **下推生成定标单** | POST | /api/stc/rfqs/{id}/push-to-award | id, selected_supplier_code | 新建D12的 id, bill_no | 对应方案S5，人工选定的供应商（默认建议最新轮次最低价） |

---

## 4. 定标单（D12） `/api/stc/awards`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/awards | status, source_bill_type, pageNo, pageSize | list[bill_no, award_supplier_name, total_award_amount, status] | |
| 详情查询 | GET | /api/stc/awards/{id} | id | 单头 + entries[中标明细分录] | |
| 新增 | POST | /api/stc/awards | source_bill_type, source_bill_no, award_supplier_code, entries[] | id, bill_no | 通常由S3/S4/S5下推生成 |
| 编辑 | PUT | /api/stc/awards/{id} | award_conclusion, entries[] | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/stc/awards/{id}/submit | id | - | |
| **审核** [远程调用·强校验] | POST | /api/stc/awards/{id}/approve | id | - | **审核前同步调用 `SrmArchiveClient.getArchive(award_supplier_code)`，若cert_status为FROZEN/BLACKLIST则拒绝审核**；通过后触发U1反写来源单据状态、U2判断是否关闭D9 |
| 驳回 | POST | /api/stc/awards/{id}/reject | id, reject_reason | - | |
| **下推生成采购合同** | POST | /api/stc/awards/{id}/push-to-contract | id | 新建D13的 id, bill_no | 对应S6，可选 |
| **下推生成价目表** | POST | /api/stc/awards/{id}/push-to-pricelist | id | 新建D14的 id, bill_no | 对应S7，可选 |
| **下推生成框架协议** | POST | /api/stc/awards/{id}/push-to-bpa | id | 新建D15的 id, bill_no | 对应S8，可选 |

---

## 5. 采购合同（D13） `/api/stc/contracts`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/contracts | status, supplier_code, contract_type, pageNo, pageSize | list[bill_no, contract_name, supplier_code, status] | |
| 详情查询 | GET | /api/stc/contracts/{id} | id | 单头 + entries[条款/标的分录] | |
| 新增 | POST | /api/stc/contracts | source_award_bill_no, supplier_code, contract_name, contract_amount, effective_date, expire_date, contract_type, entries[] | id, bill_no | |
| 编辑 | PUT | /api/stc/contracts/{id} | 同新增 + legal_review_status, seal_status | - | 仅DRAFT/IN_REVIEW可编辑 |
| 提交 | POST | /api/stc/contracts/{id}/submit | id | - | status→IN_REVIEW |
| 审核 | POST | /api/stc/contracts/{id}/approve | id | - | status→APPROVED（履约中） |
| 终止 | POST | /api/stc/contracts/{id}/terminate | id, terminate_reason | - | status→TERMINATED |
| 到期预警列表 | GET | /api/stc/contracts/expiring | daysAhead(默认30) | list[bill_no, supplier_code, expire_date] | 供定时任务/前端提醒调用 |

---

## 6. 价目表（D14） `/api/stc/price-lists`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/price-lists | status, supplier_code, pageNo, pageSize | list[bill_no, supplier_code, effective_date, status] | |
| 详情查询 | GET | /api/stc/price-lists/{id} | id | 单头 + entries[价格明细分录] | |
| 新增 | POST | /api/stc/price-lists | source_award_bill_no, supplier_code, effective_date, expire_date, entries[] | id, bill_no | |
| 编辑 | PUT | /api/stc/price-lists/{id} | 同新增 | - | 仅DRAFT可编辑 |
| **生效** | POST | /api/stc/price-lists/{id}/activate | id | - | status→EFFECTIVE；同供应商同物料的旧EFFECTIVE记录自动置为SUPERSEDED |
| 作废 | POST | /api/stc/price-lists/{id}/void | id | - | 人工终止生效 |
| **按物料查询有效价格**（供未来PTP调用） | GET | /api/stc/price-lists/effective-price | supplier_code, item_code | unit_price, currency, bill_no | PTP阶段"采购申请取价"的核心查询接口，本阶段先实现好 |

---

## 7. 框架协议 BPA（D15） `/api/stc/bpas`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/bpas | status, supplier_code, pageNo, pageSize | list[bill_no, supplier_code, total_quota_amount, used_quota_amount, status] | |
| 详情查询 | GET | /api/stc/bpas/{id} | id | 单头 + entries[适用品类分录] | |
| 新增 | POST | /api/stc/bpas | source_award_bill_no, supplier_code, total_quota_amount, effective_date, expire_date, entries[] | id, bill_no | used_quota_amount 固定初始化为0 |
| 编辑 | PUT | /api/stc/bpas/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 生效 | POST | /api/stc/bpas/{id}/activate | id | - | status→EFFECTIVE |
| 终止 | POST | /api/stc/bpas/{id}/terminate | id | - | status→TERMINATED |
| **额度累加**（预留给PTP调用，本阶段仅实现接口不接入业务） | POST | /api/stc/bpas/{id}/accumulate-usage | id, amount, source_po_bill_no | used_quota_amount(累加后) | PTP生成采购订单引用本BPA下单时调用；used_quota_amount≥total_quota_amount时status自动置EXHAUSTED |
| 额度使用明细查询 | GET | /api/stc/bpas/{id}/usage-log | id | list[amount, source_po_bill_no, occurred_at] | 配合accumulate-usage记录使用流水，本阶段建表预留 |

---

## 8. SR配额单（D16） `/api/stc/sr-allocations`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/stc/sr-allocations | status, category_code, period, pageNo, pageSize | list[bill_no, category_code, period, status] | |
| 详情查询 | GET | /api/stc/sr-allocations/{id} | id | 单头 + entries[供应商配额分录] | |
| 新增 | POST | /api/stc/sr-allocations | category_code, period, entries[{supplier_code, ratio_percent}] | id, bill_no | 校验entries的ratio_percent合计=100 |
| 编辑 | PUT | /api/stc/sr-allocations/{id} | 同新增 | - | 仅DRAFT可编辑 |
| **刷新绩效等级** [远程调用] | POST | /api/stc/sr-allocations/{id}/refresh-perf-grade | id | entries[perf_grade_ref已更新] | 逐行调用 `SrmArchiveClient.getArchive(supplierCode)` 拉取最新perf_grade，仅展示不持久化差异字段 |
| 生效 | POST | /api/stc/sr-allocations/{id}/activate | id | - | status→EFFECTIVE |

---

## 9. 事件订阅处理（无对外HTTP接口，内部消费者）

| 消费方法（内部） | 订阅的事件routing key | 处理动作 | 对应模块文档 |
|---|---|---|---|
| `onSupplierFrozen` | supplier.frozen | 标记本地缓存中该供应商为"风险"，在候选供应商分录、投标供应商分录等展示层加风险提示角标；**不自动撤销已在途单据** | 07号文档4.1节 |
| `onSupplierUnfrozen` | supplier.unfrozen | 清除风险提示角标 | 07号文档4.1节 |
| `onSupplierBlacklisted` | supplier.blacklisted | 标记为"禁止交易"风险，且在D12审核环节的同步校验会二次兜底拦截（事件+同步查询双保险） | 07号文档4.1节 |
| `onSupplierArchiveUpdated` | supplier.archive.updated | 若变更字段包含`perf_grade`，触发已生效D16单据的展示层提示"绩效等级已更新，建议刷新" | 07号文档4.1节 |

---

## 10. 与SRM接口清单的错误码延用

沿用《03-SRM接口清单.md》第9章错误码表 + 《00-技术架构与技术栈说明.md》第7章微服务专属错误码段，不重复定义。

---

## 11. 与前端页面的对应关系（简要）

| 页面 | 对应资源 | 说明 |
|---|---|---|
| 寻源立项列表/表单 | /api/stc/sourcing-projects | 候选供应商分录需展示"刷新候选供应商"按钮 |
| 招标列表/表单 | /api/stc/tenders | 需区分"投标登记"与"评标"两个页签，开标后前者锁定 |
| 询价列表/表单 | /api/stc/rfqs | 报价分录按轮次分组展示，支持"追加新一轮" |
| 定标列表/表单 | /api/stc/awards | 审核按钮需展示远程校验loading态，失败要给出明确原因（供应商已被冻结/黑名单） |
| 采购合同列表/表单 | /api/stc/contracts | 详情页展示到期倒计时 |
| 价目表列表/表单 | /api/stc/price-lists | |
| 框架协议列表/表单 | /api/stc/bpas | 详情页展示额度使用进度条（used/total） |
| SR配额列表/表单 | /api/stc/sr-allocations | 供应商配额分录需展示"绩效等级"只读列+刷新按钮 |
