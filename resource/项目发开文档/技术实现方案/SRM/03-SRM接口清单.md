# SRM模块接口清单

版本：V1.0　日期：2026-09-17

> 与《01-SRM技术方案设计文档.md》《02-SRM数据模型设计.md》配套。接口路径统一前缀 `/api/srm`，返回体统一包裹为 `{ code, message, data }`，本文档只列关键业务字段，分页统一约定 `pageNo`/`pageSize` 入参、`total`/`list` 出参，不再逐条重复。
>
> **审核/确认类接口是反写与下推的实际触发点**——AI 实现时，`approve` / `confirm` 接口内部必须在同一事务中完成：① 更新当前单据状态；② 执行第01号文档第6章对应的反写动作；③ 反写失败需整体回滚并返回错误。

---

## 1. 供应商邀请单（D1） `/api/srm/invitations`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/invitations | status, invitee_name, pageNo, pageSize | list[bill_no, invitee_name, status, invite_date, expire_date] | |
| 详情查询 | GET | /api/srm/invitations/{id} | id | 单头全部字段 + entries[品类分录] | |
| 新增 | POST | /api/srm/invitations | invitee_name, invitee_contact, invitee_phone, invitee_email, invited_by, expire_date, entries[] | id, bill_no | status默认PENDING |
| 编辑 | PUT | /api/srm/invitations/{id} | 同新增 | - | 仅PENDING可编辑 |
| 删除 | DELETE | /api/srm/invitations/{id} | id | - | 仅PENDING可删除 |
| 作废 | POST | /api/srm/invitations/{id}/decline | id | - | status → DECLINED |
| **下推生成注册单** | POST | /api/srm/invitations/{id}/push-to-registration | id | 新建D2的 id, bill_no | 对应方案T1；执行W1反写（本单status→REGISTERED，related_reg_bill_no回填） |

---

## 2. 供应商注册申请单（D2） `/api/srm/registrations`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/registrations | status, supplier_name, source_type, pageNo, pageSize | list[bill_no, supplier_name, status, apply_date] | |
| 详情查询 | GET | /api/srm/registrations/{id} | id | 单头 + entries[品类分录, 证照分录] | |
| 新增 | POST | /api/srm/registrations | supplier_name, unified_credit_code, contact_*, reg_address, cooperation_note, source_type, source_invite_bill_no, entries[] | id, bill_no | 若unified_credit_code已存在于档案且非POTENTIAL，返回警告（非阻断） |
| 编辑 | PUT | /api/srm/registrations/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 删除 | DELETE | /api/srm/registrations/{id} | id | - | 仅DRAFT可删除 |
| 提交 | POST | /api/srm/registrations/{id}/submit | id | - | status: DRAFT→SUBMITTED |
| **下推生成认证单** | POST | /api/srm/registrations/{id}/push-to-certification | id | 新建D3的 id, bill_no | 对应方案T2；本单status→IN_CERT，重复调用返回"已存在下游认证单"错误 |

---

## 3. 供应商认证单（D3） `/api/srm/certifications`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/certifications | status, supplier_name, conclusion, pageNo, pageSize | list[bill_no, supplier_name, cert_type, conclusion, status] | |
| 详情查询 | GET | /api/srm/certifications/{id} | id | 单头 + entries[认证项分录] | |
| 新增（复审场景无来源单） | POST | /api/srm/certifications | supplier_name, unified_credit_code, cert_type, start_date, responsible_person, entries[] | id, bill_no | source_bill_no为空时cert_type需为RENEWAL且需人工指定supplier_code |
| 编辑 | PUT | /api/srm/certifications/{id} | 同新增 + conclusion, valid_until | - | 仅DRAFT可编辑；conclusion可由分录结果汇总建议 |
| 提交 | POST | /api/srm/certifications/{id}/submit | id | - | status→IN_REVIEW |
| **审核通过** | POST | /api/srm/certifications/{id}/approve | id | 关联/新建的D4 supplier_code | status→APPROVED；触发T3自动生成/更新供应商档案 + W2反写，事务性执行 |
| 驳回 | POST | /api/srm/certifications/{id}/reject | id, reject_reason | - | status→REJECTED |
| 反审核 | POST | /api/srm/certifications/{id}/unapprove | id | - | 仅当D4未被后续单据变更时允许，否则拦截并提示 |

---

## 4. 供应商档案（D4） `/api/srm/archives`

> 档案是主数据，不走提交/审核流，以下接口主要用于查询与管理员维护。

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/archives | supplier_name, cert_status, perf_grade, category_code, pageNo, pageSize | list[supplier_code, supplier_name, cert_status, perf_grade, cert_valid_until] | |
| 详情查询 | GET | /api/srm/archives/{supplierCode} | supplierCode | 单头 + entries[品类/证照/联系人分录] | |
| 管理员直改 | PUT | /api/srm/archives/{supplierCode} | 任意可编辑字段 | - | 需管理员权限，操作写入审计日志，标注"人工直改"来源 |
| 关联单据历史 | GET | /api/srm/archives/{supplierCode}/related-bills | supplierCode, billType(可选) | list[billType, bill_no, status, date] | 汇总该供应商在D2/D3/D5/D6/D7/D8下的历史单据，便于溯源 |
| 证照到期预警列表 | GET | /api/srm/archives/expiring-certs | daysAhead(默认30) | list[supplier_code, cert_type, valid_to] | 供定时任务/前端提醒调用 |

---

## 5. 供应商变更单（D5） `/api/srm/change-requests`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/change-requests | status, supplier_code, change_type, pageNo, pageSize | list[bill_no, supplier_code, change_type, status] | |
| 详情查询 | GET | /api/srm/change-requests/{id} | id | 单头 + entries[变更字段分录] | |
| 新增 | POST | /api/srm/change-requests | supplier_code, change_type, change_reason, applicant, entries[] | id, bill_no | 若该供应商存在未完结变更单，返回警告 |
| 编辑 | PUT | /api/srm/change-requests/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/srm/change-requests/{id}/submit | id | - | status→SUBMITTED/IN_REVIEW |
| **审核通过** | POST | /api/srm/change-requests/{id}/approve | id | - | 触发W3：按entries逐行把new_value覆盖写入D4对应字段 |
| 驳回 | POST | /api/srm/change-requests/{id}/reject | id, reject_reason | - | |
| 反审核 | POST | /api/srm/change-requests/{id}/unapprove | id | - | |

---

## 6. 供应商冻结/解冻申请单（D6） `/api/srm/freeze-requests`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/freeze-requests | status, supplier_code, apply_type, pageNo, pageSize | list[bill_no, supplier_code, apply_type, status] | |
| 详情查询 | GET | /api/srm/freeze-requests/{id} | id | 单头 + entries[冻结范围分录] | |
| 新增 | POST | /api/srm/freeze-requests | supplier_code, apply_type, trigger_source, source_perf_bill_no, reason, entries[] | id, bill_no | apply_type=UNFREEZE时校验当前cert_status必须为FROZEN，否则拒绝创建 |
| 编辑 | PUT | /api/srm/freeze-requests/{id} | 同新增 + rectification_note | - | 仅DRAFT可编辑；UNFREEZE场景rectification_note必填 |
| 提交 | POST | /api/srm/freeze-requests/{id}/submit | id | - | |
| **审核通过** | POST | /api/srm/freeze-requests/{id}/approve | id | 更新后的D4.cert_status | 触发W4/W5：FREEZE→档案状态置FROZEN并记录pre_status_snapshot；UNFREEZE→档案状态恢复为pre_status_snapshot |
| 驳回 | POST | /api/srm/freeze-requests/{id}/reject | id, reject_reason | - | |
| 反审核 | POST | /api/srm/freeze-requests/{id}/unapprove | id | - | |

---

## 7. 供应商黑名单单（D7） `/api/srm/blacklists`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/blacklists | status, supplier_code, trigger_source, pageNo, pageSize | list[bill_no, supplier_code, trigger_source, status] | |
| 详情查询 | GET | /api/srm/blacklists/{id} | id | 单头 + entries[证据材料分录] | |
| 新增 | POST | /api/srm/blacklists | supplier_code, trigger_source, source_perf_bill_no, reason, applicant, approval_level, is_permanent, entries[] | id, bill_no | |
| 编辑 | PUT | /api/srm/blacklists/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/srm/blacklists/{id}/submit | id | - | |
| **审核通过** | POST | /api/srm/blacklists/{id}/approve | id | - | 触发W6：档案状态直接覆盖为BLACKLIST（最高优先级） |
| 驳回 | POST | /api/srm/blacklists/{id}/reject | id, reject_reason | - | |
| 反审核（管理员） | POST | /api/srm/blacklists/{id}/unapprove | id, confirm=true | - | 需二次确认参数，仅管理员角色可调用 |

---

## 8. 供应商绩效考核单（D8） `/api/srm/performance-evaluations`

| 接口名称 | Method | Path | 关键入参 | 关键出参 | 说明 |
|---|---|---|---|---|---|
| 列表查询 | GET | /api/srm/performance-evaluations | status, supplier_code, period, grade, pageNo, pageSize | list[bill_no, supplier_code, period, total_score, grade, status] | |
| 详情查询 | GET | /api/srm/performance-evaluations/{id} | id | 单头 + entries[考核维度分录] | |
| 新增 | POST | /api/srm/performance-evaluations | supplier_code, period, eval_type, evaluator, entries[] | id, bill_no, total_score(自动计算) | 校验entries权重合计=100 |
| 编辑 | PUT | /api/srm/performance-evaluations/{id} | 同新增 | - | 仅DRAFT可编辑 |
| 提交 | POST | /api/srm/performance-evaluations/{id}/submit | id | - | status→SUBMITTED |
| **确认** | POST | /api/srm/performance-evaluations/{id}/confirm | id | - | status→CONFIRMED；触发W7：档案perf_grade覆盖、perf_eval_count+1、last_perf_date覆盖 |
| **下推冻结申请单** | POST | /api/srm/performance-evaluations/{id}/push-to-freeze | id | 新建D6的 id, bill_no | 对应T4，仅建议触发（grade=C时前端提示，非强制） |
| **下推黑名单单** | POST | /api/srm/performance-evaluations/{id}/push-to-blacklist | id | 新建D7的 id, bill_no | 对应T5，人工判断触发 |

---

## 9. 通用错误码约定（建议）

| 错误码 | 说明 |
|---|---|
| 40001 | 参数校验失败（必填缺失/枚举值非法/分录权重合计不为100等） |
| 40901 | 单据状态不允许当前操作（如非DRAFT状态调用编辑接口） |
| 40902 | 重复下推（源单据已存在下游单据） |
| 40903 | 反写目标不存在或状态冲突（如对非FROZEN供应商发起UNFREEZE） |
| 40904 | 存在未完结的关联单据，操作被拦截（如变更单/删除档案前的并发校验） |
| 50001 | 服务端异常，事务已回滚 |

---

## 10. 与前端页面的对应关系（简要）

| 页面 | 对应资源 | 说明 |
|---|---|---|
| 供应商邀请列表/表单 | /api/srm/invitations | |
| 供应商注册列表/表单 | /api/srm/registrations | |
| 供应商认证列表/表单 | /api/srm/certifications | 表单需展示"来源单据"只读引用 |
| 供应商档案列表/详情 | /api/srm/archives | 详情页展示"关联单据历史"标签页 |
| 供应商变更列表/表单 | /api/srm/change-requests | 分录"变更字段"下拉需读取D4字段字典 |
| 供应商冻结解冻列表/表单 | /api/srm/freeze-requests | UNFREEZE表单需展示pre_status_snapshot只读字段 |
| 供应商黑名单列表/表单 | /api/srm/blacklists | 需要角色权限控制审核按钮可见性 |
| 供应商绩效考核列表/表单 | /api/srm/performance-evaluations | 表单需实时计算total_score并校验权重合计 |
