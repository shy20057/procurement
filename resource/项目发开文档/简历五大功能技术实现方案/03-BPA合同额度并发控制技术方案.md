# BPA合同额度并发占用、释放与采购配额统计技术方案

> 日期：2026-10-17。性质：待复现设计。
> 目标：多个采购员并行引用同一BPA下单时不超占，撤销或作废可追溯释放，同时统计供应商采购份额。
> 架构：寻源服务独占BPA额度权威数据，通过本地原子更新控制预占，采购服务以可恢复状态机协调跨服务提交；配额统计异步更新，价目表提供价格快照。
> 技术基线：Java 17、Spring Boot 3.0.7、MyBatis-Plus、PostgreSQL、RabbitMQ；Redis不承担最终额度正确性。
> 实施约束：本轮只写设计；后续测试、联调和压测先取得用户批准，以下验收表不是执行结果。

## 1. 客户业务场景

集团每年与设备供应商A签订框架采购协议BPA，允许多个工厂在有效期内分批采购工业控制器。例如协议总金额500万元，已确认订单400万元，剩余100万元。采购员甲提交80万元订单，乙同时提交60万元订单，两张都单独检查“剩余100万元”时可能同时通过，导致承诺采购金额达到540万元。

客户要求不是简单显示一个剩余数字，而是确保任何时刻有效占用不能超过框架协议上限；订单提交或接口重试不能重复占用；撤销、作废不能重复释放。业务需要看清每笔订单占用了多少，异常时能对账恢复。

同一种控制器还可能有供应商A、B两个来源。采购策略规定季度采购金额目标份额为60%与40%，希望看到实际份额偏差。这是采购配额统计，与BPA合同上限分属两个规则。

订单价格来自有效价目表或BPA约定价格。客户还要求历史订单可证明当时采用了哪一版价格，即使价目表以后变化，原订单价格不能跟着变。

这是根据插件业务整理的复现场景；示例金额、供应商份额和组织规模用于说明，不作为已核实的生产数据。

## 2. 原代码依据与当前项目差异

实习源码根目录：`E:/Intern_workspace_26y/cosmic_project/project4demo/code/esc/sshh-pro44-esc-app44/src/main/java/sshh/pro44/esc/app44/`。

| 原插件 | 已有业务 |
| --- | --- |
| `plugin/operate/PurOrderWriteBackBpaPlugin.java` | 提交按BPA号和行号累计已订货数量，撤销时回冲；赠品行不参与 |
| `plugin/operate/PurOrderBizCancelWriteBackBpaPlugin.java` | 业务作废时按BPA分录汇总扣减已订货数量 |
| `plugin/operate/PurOrderWriteBackQuotaPlugin.java` | 按物料、供应商、有效期反写已采购数量并计算实际占比 |
| `plugin/form/PurOrderPullContractFormPlugin.java` | 合同付款条件、发票类型及贸易条款带入订单 |

原代码按数量管理，且先校验、后在操作回调中读取保存；回调自身没有展示足以证明严格并发控制的锁和幂等流水。因此只能说存在并发超占风险，不能在未执行并发验证时声称已经复现了生产事故。

当前工程不同：

- BPA在`stc_schema.t_stc_bpa_h`，字段是`total_quota_amount/used_quota_amount`，首期按金额管理。
- 明细表`t_stc_bpa_e_cat`具有`max_qty`，可作为第二阶段数量上限，但目前没有已用数量与占用流水。
- 供应商配额`t_stc_supplier_ratio_h/e`以采购品类、周期和比例为主，未存实际执行额。
- `procurement-service`尚需补充独立订单与订单行持久化，不能直接假设已有完整采购提交业务。

首期遵循当前金额模型，供应商配额按同一品类周期、统一CNY净采购金额统计。原项目按物料数量的统计作为业务来源说明，不能把不同计量单位的数量相加算份额。

## 3. 三类对象的责任

| 对象 | 解决的问题 | 提交时行为 | 一致性要求 |
| --- | --- | --- | --- |
| BPA | 与供应商约定的采购上限 | 原子预占、确认、释放 | STC内部强一致，跨服务可恢复协调 |
| 供应商配额 | 采购份额是否符合60/40策略 | 订单事件更新实际执行额、偏差预警 | 最终一致；首期不硬性阻断订单 |
| 价目表 | 物料在有效期内该用什么价格 | 查询校验并固化版本/价格/税口径 | 订单自身事务保存快照 |

首期金额统一为协议币种、未税净额；订单币种必须与BPA一致，跨币种额度折算不在首期。配额示例只纳入CNY协议，后续如支持多币种，须先规定固定汇率类型、日期和统计快照。

赠品行额度金额为0，但保留数量与来源；一般订单按`数量×未税单价`以BigDecimal逐行计算，按2位小数HALF_UP固化，再汇总金额。不能用请求端传入的总金额作为最终扣减值。

## 4. 服务边界：修正一个关键前提

BPA由`sourcing-service`拥有，订单由`procurement-service`拥有。即使聚合模式共用一个PostgreSQL实例，也不让采购服务直接更新STC表。

此前讨论中的“订单提交与BPA扣减放在同一个本地事务”只适用于数据本来就在同一个业务服务的架构。本项目明确服务边界，所以正式方案采用以下路径：

```text
PTP保存提交意图 → STC预占额度 → PTP持久化提交决定
  → STC确认占用 → PTP标记提交成功并发布事件
```

在STC内部，额度更新与占用记录同事务；在PTP内部，订单状态与Outbox同事务。HTTP调用不能参加两边的本地事务。聚合模式也通过相同Port接口协调，独立模式替换为HTTP适配器。

## 5. 业务状态机

PTP订单提交状态：

```text
DRAFT → SUBMITTING → SUBMIT_DECIDED → SUBMITTED
                 → ABORT_DECIDED → DRAFT
SUBMITTED → CANCEL_PENDING → CANCELLED
```

STC占用状态：

```text
HELD → CONFIRMED → RELEASED
HELD → CANCELLED
不存在 → CANCELLED墓碑（防止取消先到、预占后到）
```

业务规定：草稿不占额度；HELD已经减少可用额度；只有STC确认成功、PTP进入SUBMITTED后才向后续审批或供应商开放。首期若需要审批，驳回通过取消流程释放；已有收货或后续履约的订单禁止直接全部释放，后续扩展为可取消未履约部分。

每次提交生成不可变`submission_id`，订单撤回后再次提交使用新ID。旧提交的迟到确认和释放不能作用到新提交。

## 6. 数据模型

### 6.1 STC额度与占用

保留`used_quota_amount`表示已确认且尚未释放的金额，新增`reserved_quota_amount`表示HELD金额：

```sql
alter table stc_schema.t_stc_bpa_h
  add column reserved_quota_amount numeric(19,2) not null default 0,
  add column version bigint not null default 0;

alter table stc_schema.t_stc_bpa_h
  add constraint ck_bpa_amount_balance check (
    used_quota_amount >= 0 and reserved_quota_amount >= 0
    and used_quota_amount + reserved_quota_amount <= total_quota_amount
  );

create table stc_schema.bpa_reservation (
  reservation_id uuid primary key,
  submission_id uuid not null unique,
  order_id uuid not null,
  order_revision bigint not null,
  supplier_code varchar(32) not null,
  currency varchar(10) not null,
  request_hash varchar(64) not null,
  state varchar(20) not null,
  created_at timestamptz not null default clock_timestamp(),
  updated_at timestamptz not null default clock_timestamp(),
  check (state in ('HELD','CONFIRMED','CANCELLED','RELEASED'))
);

create table stc_schema.bpa_reservation_item (
  id bigserial primary key,
  reservation_id uuid not null references stc_schema.bpa_reservation(reservation_id),
  bpa_id bigint not null references stc_schema.t_stc_bpa_h(id),
  amount numeric(19,2) not null check (amount > 0),
  unique (reservation_id, bpa_id)
);
```

当前已有非零`used_quota_amount`的示例数据上线前需核对并形成期初占用记录，不把初始金额强行清零以通过约束。历史超额数据先报告并处理，再建立余额约束。

取消先于预占到达时，仍创建CANCELLED头记录，填写submissionId、订单身份、供应商、币种和原预占请求摘要，但不写占用明细；后到的预占读取该墓碑返回已取消，不再次扣减。取消命令携带原请求身份和摘要，以识别不同业务请求误用同一幂等键。

### 6.2 PTP订单与提交意图

拟建`ptp_schema.purchase_order`、`purchase_order_line`、`po_submission`：

| 表 | 关键字段与约束 |
| --- | --- |
| `purchase_order` | id、bill_no唯一、supplier_code、currency、status、version、active_submission_id |
| `purchase_order_line` | order_id、line_no唯一、item_code、category_code、qty、unit_price、line_amount、bpa_id、price_source_id、price_source_version |
| `po_submission` | submission_id主键、order_id、order_revision、decision、request_hash、reservation_id、next_action_at、last_error |
| `outbox_event` | event_id主键、aggregate_id、aggregate_version、event_type、payload、发布租约、重试时间 |

`decision`为UNDECIDED/SUBMIT/ABORT；订单当前revision与submissionId共同防止迟到结果污染新版本。订单进入SUBMITTING后锁定价格、数量与BPA引用，不允许边提交边编辑。

### 6.3 采购份额投影

新增`stc_schema.supplier_ratio_order_projection`，以`order_id`为唯一键保存最新`aggregate_version/status`及按供应商、品类、周期汇总的金额。新增`inbox_event(consumer_name,event_id)`复合唯一键。

事件携带订单当前有效贡献的完整快照。消费时只有版本更高才替换投影；重复和旧版本无效。按品类周期汇总投影并计算实际份额，首期直接SQL聚合，不额外保存容易漂移的多行百分比。

```text
实际份额 = 该供应商有效订单金额 / 同品类同周期全部供应商有效订单金额
```

总额为0时份额为0，显示“暂无采购”；目标份额作为管理策略，实际份额暂时超出只预警。

## 7. 预占与释放的核心事务

### 7.1 预占

1. 首先插入以submissionId唯一的占用记录。采用`ON CONFLICT DO NOTHING`后读取已有记录；若采用抛唯一冲突的写法，必须先回滚事务再查询。请求摘要不同返回409，同请求返回既有结果。
2. 同一张订单中引用相同BPA的多行先汇总，按bpaId升序处理。
3. 在STC本地短事务中校验供应商、币种、有效期、物料/品类范围，执行原子条件更新。
4. 任意BPA不足则整个预占事务回滚；成功后保存占用明细并标记HELD。

```sql
update stc_schema.t_stc_bpa_h
set reserved_quota_amount = reserved_quota_amount + :amount,
    version = version + 1,
    updated_at = clock_timestamp()
where id = :bpaId
  and supplier_code = :supplierCode
  and currency = :currency
  and status = 'EFFECTIVE'
  and valid_from <= :businessDate and valid_to >= :businessDate
  and used_quota_amount + reserved_quota_amount + :amount <= total_quota_amount
returning id;
```

金额必须大于0；0元赠品不执行此SQL。更新0行返回具体业务错误，不盲目无限重试。PostgreSQL条件更新本身即可防止超占，不必再附加版本比较制造热点CAS重试；`version`用于审计与普通编辑控制。规则依据见[事务隔离文档](https://www.postgresql.org/docs/current/transaction-iso.html)。

### 7.2 确认、取消与释放

先锁占用头记录再按bpaId排序更新BPA，所有路径锁顺序一致。状态比较和余额变化同事务：

| 操作 | 前置状态 | 余额变化 |
| --- | --- | --- |
| confirm | HELD | reserved减、used加，总占用不变 |
| cancel | HELD | reserved减，状态CANCELLED |
| release | CONFIRMED | used减，状态RELEASED |
| 重复同操作 | 已是目标状态 | 返回原结果，无余额变化 |
| cancel确认过的占用 | CONFIRMED | 返回冲突；必须走明确的订单取消/release流程 |

释放量从服务端原占用明细读取，不接受调用者任填金额；余额非负约束作为最后防线。合同过期或终止仍允许合法释放；不能因为当前合同状态失效而无法退回历史占用。

## 8. 跨服务提交与故障恢复

完整时序：

1. PTP事务锁订单并校验DRAFT/version，生成submissionId和不可变金额快照，置SUBMITTING。
2. 事务外请求STC预占。调用超时则通过submissionId查状态，同一ID重试，不生成新ID。
3. 预占成功后，PTP事务把decision从UNDECIDED改成SUBMIT，订单置SUBMIT_DECIDED，写确认命令到Outbox。
4. STC幂等确认，PTP通过响应或恢复查询确认后置SUBMITTED，写`PurchaseOrderStateChanged`完整状态事件。
5. 若在提交决定前业务校验失败，PTP用CAS写ABORT决定，再可靠发送cancel。cancel确认后回到DRAFT。

“决定”一旦持久化不可因为网络超时随意反转。恢复器看到SUBMIT决定只继续确认，看到ABORT只继续取消。不能只因HELD超过十分钟就自动释放：PTP可能已经作出提交决定，只是确认消息还在路上。

清理长时间HELD时先调用PTP查询该submission的持久化决定。UNDECIDED超时由PTP协调器在本地CAS决定ABORT后取消；未知、网络不可达或返回矛盾则保留占用并告警。宁可短暂少卖，也不在未知状态下重复出售额度。

正式订单取消时，PTP先事务性进入CANCEL_PENDING，停止下发与新履约，再发release；STC释放成功后PTP置CANCELLED。若释放成功后PTP崩溃，恢复器查询并完成终态，不能让订单重新回到可履约状态。

## 9. 接口契约

| 服务 | 接口 | 语义 |
| --- | --- | --- |
| PTP | `POST /api/ptp/orders/{id}/submit` | 携带Idempotency-Key和expectedVersion，返回202及submissionId |
| PTP | `GET /api/ptp/submissions/{submissionId}` | 查询持久化决定和进度 |
| PTP | `POST /api/ptp/orders/{id}/cancel` | 校验未履约，建立取消意图 |
| STC内部 | `POST /internal/stc/bpa-reservations` | submissionId、订单版本、供应商、币种、分项金额 |
| STC内部 | `GET /internal/stc/bpa-reservations/by-submission/{id}` | 查未知结果 |
| STC内部 | `POST /internal/stc/bpa-reservations/{submissionId}/confirm` | 幂等确认 |
| STC内部 | `POST /internal/stc/bpa-reservations/{submissionId}/cancel` | 取消HELD，支持墓碑 |
| STC内部 | `POST /internal/stc/bpa-reservations/{submissionId}/release` | 释放CONFIRMED，需要取消操作ID |
| STC查询 | `GET /api/stc/bpa/{id}/balance` | total、reserved、used、available |

内部API必须进行服务身份校验，不能由供应商门户直接调用；后端重新计算可信金额，防止用户把金额改小以绕过额度。

## 10. 配额消息与价目表处理

订单事件共用领域Exchange `procurement.domain.v1`，路由`ptp.order.state-changed.v1`，队列`stc.ratio.order-state.v1`。消息包含`eventId/orderId/aggregateVersion/status/submissionId/lines/occurredAt/schemaVersion`。

消费者先插入inbox，再按订单版本更新完整贡献快照，同一事务提交后ACK。若CANCELLED版本先到、SUBMITTED旧版本后到，旧消息被忽略，不会把已取消订单重新计入。

当前工程已有Outbox表与Exchange配置，但尚未发现完整可复用投递器；复现必须补充发布租约、Confirm、mandatory Return、超时重投和消费者inbox。通用实现语义与04一致，各服务只写自己的Outbox表。

价目解析优先级首期规定为：订单明确引用有效BPA行固定价，其次有效价目表；多条规则同时命中且没有明确优先级时阻断选择，不随机取第一条。返回来源ID和版本，PTP提交前复核并固定快照；价格变化返回409要求用户确认，不能后台悄悄加价。

## 11. 文件与迁移落点

| 路径 | 职责 |
| --- | --- |
| `backend/sourcing-service/src/main/java/com/example/procurement/sourcing/service/bpa/` | 在现有查询服务旁增加独立`BpaReservationService`，不把额度逻辑塞入Controller |
| `backend/sourcing-service/src/main/java/com/example/procurement/sourcing/controller/bpa/` | 增加内部占用API与余额查询 |
| `backend/sourcing-service/src/main/java/com/example/procurement/sourcing/service/supplierratio/` | 新增订单状态投影消费者与实际比例查询 |
| `backend/sourcing-service/src/main/resources/db/stc/migration/V2__business_messaging.sql` | STC Outbox、Inbox与业务审计基础 |
| `backend/sourcing-service/src/main/resources/db/stc/migration/V3__bpa_reservation.sql` | 预占余额、占用记录、配额投影 |
| `backend/procurement-service/src/main/java/com/example/procurement/purchasing/order/` | 订单聚合、提交协调器、STC Port与HTTP客户端 |
| `backend/procurement-service/src/main/resources/db/ptp/migration/V1__orders_and_messaging.sql` | PTP订单、提交意图、Outbox/Inbox |
| `backend/procurement-service/pom.xml` | 补Validation、持久化、Flyway和AMQP依赖 |
| `backend/sourcing-service/pom.xml` | 补AMQP依赖 |
| `backend/aggregation-service/src/main/java/com/example/procurement/aggregation/AggregationServiceApplication.java` | 补PTP扫描和独立迁移，不允许隐式跨schema事务依赖 |
| `web/src/views/stc/BpaDetail.vue` | 展示预占、已确认、可用额度与流水 |
| `web/src/views/ptp/` | 新建订单页与提交中、取消中状态展示 |

所有新增Flyway版本在实际实施前核对当前最大版本；保留已执行的V1，不修改既有迁移。聚合模式可共用连接，但服务间只有Port调用；独立模式各自账号只能访问自己的schema。

## 12. 复现顺序

- [ ] 实现订单表、行金额快照与乐观锁编辑；准备有效BPA和价目表。
- [ ] 实现STC预占/确认/取消/释放状态机、原子余额与唯一流水。
- [ ] 完成PTP提交协调器与持久化决定，支持查未知结果。
- [ ] 完成Outbox投递及重启恢复，补取消意图和墓碑处理。
- [ ] 消费订单完整状态快照，展示供应商配额实际份额。
- [ ] 建立“BPA余额=有效占用记录汇总”的只读对账与人工修复审批入口。

## 13. 待批准的验收案例

| 场景 | 必须满足的业务不变量 |
| --- | --- |
| 剩余100万元，80万与60万同时提交 | 最多一个预占成功，used+reserved不超过总额 |
| 同submissionId重复预占100次 | 只产生一笔占用，参数不同返回409 |
| 多行、多BPA中最后一项不足 | 同一预占事务全部回滚 |
| STC预占成功但响应丢失 | PTP查原ID恢复，不创建第二笔占用 |
| PTP持久化SUBMIT后进程退出 | 重启继续confirm，不超时释放 |
| cancel先于reserve到达 | 墓碑阻止迟到预占 |
| 订单撤回后再次提交 | 新submissionId，旧响应不影响新提交 |
| 取消重复执行 | 只释放一次，订单不可再次履约 |
| 配额消息重复、乱序 | 份额等于最新订单快照汇总 |

性能数据后续分别记录热点单BPA与分散BPA的成功吞吐、P95/P99、锁等待、冲突/业务拒绝数及恢复耗时。硬额度热点天然需要串行决策，不通过盲目堆Redis锁宣称无限吞吐。

## 14. 面试讲解与简历口径

“集团多个采购组织共用框架协议下单，需要防止累计订单超过协议金额。我把BPA作为寻源服务的权威资源，用数据库条件更新保证预占不超额，以submissionId流水保证重试与释放幂等。订单和BPA属于两个服务，所以用持久化提交决定协调预占、确认和取消；份额统计通过订单完整状态事件异步更新，价目只提供不可变价格快照。”

复现完成后可写：“实现BPA合同额度并发预占与幂等释放，通过原子条件更新、占用流水和跨服务提交恢复防止超额下单；异步维护供应商配额执行统计。”若只完成单服务版本，则不声称完成跨服务协调。

## 15. 技术依据与关联

数据库行锁与固定锁顺序参考[PostgreSQL显式锁](https://www.postgresql.org/docs/current/explicit-locking.html)，条件更新在并发下的判断参考[PostgreSQL事务隔离](https://www.postgresql.org/docs/current/transaction-iso.html)。状态机及业务口径为本项目设计。

关联：[02-任务恢复](./02-分布式调度与失败补偿技术方案.md)、[04-Outbox与外部调用](./04-采购验收可靠异步推送SAP技术方案.md)、[05-供应商报价](./05-供应商集中报价高并发处理技术方案.md)。
