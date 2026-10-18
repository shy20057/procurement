# SRM模块开发实施计划

版本：V1.0　日期：2026-09-17　范围：SRM 供应商关系管理 · 第一阶段开发

---

## 一、架构决策

**方案：在 supplier-service 上扩展SRM模块**

决策理由：
1. 复用现有框架代码，减少维护成本
2. API路径使用 `/api/srm/...`，与现有 `/api/v1/suppliers/...` 完全分离
3. 数据库schema使用 `srm_schema`，与原有 supplier_schema 分离
4. 端口保持 8082，通过api-gateway路由区分

---

## 二、模块架构设计

```
supplier-service (端口8082)
├── 框架组件（已复用）
│   ├── ApiExceptionHandler
│   ├── MessagingConfiguration (RabbitMQ)
│   ├── OutboxEvent / OutboxRepository
│   └── OutboxPublisher
└── SRM模块（新增8张单据）
    ├── D1 供应商邀请单     → t_srm_supplier_invite_h / _e_cat
    ├── D2 供应商注册申请单 → t_srm_supplier_reg_h / _e_cat / _e_cert
    ├── D3 供应商认证单     → t_srm_supplier_cert_h / _e
    ├── D4 供应商档案      → t_srm_supplier_archive_h / _e_cat / _e_cert / _e_contact
    ├── D5 供应商变更单     → t_srm_supplier_change_h / _e
    ├── D6 供应商冻结解冻单 → t_srm_supplier_freeze_h / _e
    ├── D7 供应商黑名单单   → t_srm_supplier_blacklist_h / _e
    └── D8 绩效考核单      → t_srm_supplier_perf_h / _e
```

---

## 三、开发阶段规划

### Phase 1: 项目基础设施搭建
- [x] 1.1 清理原有的简化版supplier代码
- [x] 1.2 保留框架组件（ApiExceptionHandler、Outbox等）
- [x] 1.3 修改 application.yml（schema: srm_schema）
- [x] 1.4 更新 OutboxEvent schema 引用
- [x] 1.5 更新 api-gateway 路由 `/api/srm/**` → supplier-service

### Phase 2: 数据库设计与迁移
- [x] 2.1 设计Flyway迁移脚本 `V1__srm_baseline.sql`
- [x] 2.2 创建19张表（8张单头+11张分录）
- [x] 2.3 添加索引（状态索引、供应商编码索引等）
- [x] 2.4 添加审计字段和版本控制

### Phase 3: 领域实体开发
- [x] 3.1 D1 供应商邀请单实体（单头+分录）
- [x] 3.2 D2 供应商注册申请单实体（单头+2分录）
- [x] 3.3 D3 供应商认证单实体（单头+分录）
- [x] 3.4 D4 供应商档案实体（单头+3分录）
- [x] 3.5 D5 供应商变更单实体（单头+分录）
- [x] 3.6 D6 供应商冻结解冻单实体（单头+分录）
- [x] 3.7 D7 供应商黑名单单实体（单头+分录）
- [x] 3.8 D8 绩效考核单实体（单头+分录）

### Phase 4: Repository层开发
- [x] 4.1 为8张单头表创建Repository
- [x] 4.2 为11张分录表创建Repository
- [x] 4.3 添加自定义查询方法

### Phase 5: ApplicationService层开发（核心业务逻辑）
- [x] 5.1 编号生成服务（BillNoGenerator）
- [x] 5.2 D1 邀请单服务（状态机、下推T1、反写W1）
- [x] 5.3 D2 注册单服务（状态机、下推T2）
- [x] 5.4 D3 认证单服务（状态机、T3自动下推/反写W2）
- [x] 5.5 D4 档案服务（主档案管理、关联单据查询）
- [x] 5.6 D5 变更单服务（状态机、反写W3）
- [x] 5.7 D6 冻结解冻服务（状态机、反写W4/W5）
- [x] 5.8 D7 黑名单服务（状态机、反写W6）
- [x] 5.9 D8 绩效考核服务（状态机、反写W7、T4/T5下推）
- [x] 5.10 事务一致性保障（同一事务内完成状态变更+反写）

### Phase 6: Controller层开发（REST API）
- [x] 6.1 D1 邀请单API（新增/作废/下推）
- [x] 6.2 D2 注册单API（新增/提交/下推）
- [x] 6.3 D3 认证单API（新增/提交/审核/驳回）
- [x] 6.4 D4 档案API（详情/列表/证照到期预警）
- [x] 6.5 D5 变更单API（新增/提交/审核/驳回）
- [x] 6.6 D6 冻结解冻单API（新增/提交/审核/驳回）
- [x] 6.7 D7 黑名单单API（新增/提交/审核/驳回）
- [x] 6.8 D8 绩效考核单API（新增/提交/确认）

---

## 四、技术实现要点

### 4.1 编号生成规则
```
D1: SRM-INV-YYYYMMDD-####
D2: SRM-REG-YYYYMMDD-####
D3: SRM-CERT-YYYYMMDD-####
D4: SUP####### (7位序号)
D5: SRM-CHG-YYYYMMDD-####
D6: SRM-FRZ-YYYYMMDD-####
D7: SRM-BLK-YYYYMMDD-####
D8: SRM-PERF-YYYYMMDD-####
```

### 4.2 状态机核心逻辑

**通用状态机（适用于D2-D7）**
```
DRAFT → SUBMITTED → IN_REVIEW → APPROVED
                              ↘ REJECTED → DRAFT
APPROVED → UNAPPROVED → DRAFT
```

**D1邀请单专用状态机**
```
PENDING → REGISTERED / DECLINED / EXPIRED
```

**D8绩效考核单专用状态机**
```
DRAFT → SUBMITTED → CONFIRMED (无驳回态)
```

### 4.3 下推与反写事务保障

审核/确认接口必须在同一事务内完成：
1. 更新当前单据状态
2. 执行对应的反写动作
3. 反写失败需整体回滚

### 4.4 供应商档案状态优先级

```
BLACKLIST（黑名单） > FROZEN（冻结） > QUALIFIED / TEMP_QUALIFIED（合格/临时合格） > POTENTIAL（潜在）
```

---

## 五、数据库表清单（19张）

| # | 表名 | 所属单据 | 类型 |
|---|---|---|---|
| 1 | t_srm_supplier_invite_h | D1 | 单头 |
| 2 | t_srm_supplier_invite_e_cat | D1 | 分录-品类 |
| 3 | t_srm_supplier_reg_h | D2 | 单头 |
| 4 | t_srm_supplier_reg_e_cat | D2 | 分录-品类 |
| 5 | t_srm_supplier_reg_e_cert | D2 | 分录-证照 |
| 6 | t_srm_supplier_cert_h | D3 | 单头 |
| 7 | t_srm_supplier_cert_e | D3 | 分录-认证项 |
| 8 | t_srm_supplier_archive_h | D4 | 单头 |
| 9 | t_srm_supplier_archive_e_cat | D4 | 分录-品类 |
| 10 | t_srm_supplier_archive_e_cert | D4 | 分录-证照 |
| 11 | t_srm_supplier_archive_e_contact | D4 | 分录-联系人 |
| 12 | t_srm_supplier_change_h | D5 | 单头 |
| 13 | t_srm_supplier_change_e | D5 | 分录 |
| 14 | t_srm_supplier_freeze_h | D6 | 单头 |
| 15 | t_srm_supplier_freeze_e | D6 | 分录 |
| 16 | t_srm_supplier_blacklist_h | D7 | 单头 |
| 17 | t_srm_supplier_blacklist_e | D7 | 分录 |
| 18 | t_srm_supplier_perf_h | D8 | 单头 |
| 19 | t_srm_supplier_perf_e | D8 | 分录 |

---

## 六、API接口清单

| 前缀 | 单据 | 接口数 |
|---|---|---|
| /api/srm/invitations | D1 供应商邀请单 | 8 |
| /api/srm/registrations | D2 供应商注册申请单 | 7 |
| /api/srm/certifications | D3 供应商认证单 | 9 |
| /api/srm/archives | D4 供应商档案 | 5 |
| /api/srm/change-requests | D5 供应商变更单 | 8 |
| /api/srm/freeze-requests | D6 供应商冻结解冻申请单 | 8 |
| /api/srm/blacklists | D7 供应商黑名单单 | 8 |
| /api/srm/performance-evaluations | D8 供应商绩效考核单 | 8 |

---

## 七、开发顺序

```
Phase 1: 项目基础设施
Phase 2: 数据库迁移脚本
Phase 3: 领域实体
Phase 4: Repository层
Phase 5: ApplicationService层
Phase 6: Controller层
```

---

## 八、参考文档

- 《01-SRM技术方案设计文档.md》
- 《02-SRM数据模型设计.md》
- 《03-SRM接口清单.md》
