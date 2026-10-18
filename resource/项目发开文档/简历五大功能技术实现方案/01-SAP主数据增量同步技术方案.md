# SAP成本中心与汇率增量同步技术方案

> 日期：2026-10-17。性质：技术设计，包含已实现主线及尚未实现的可选方案；具体完成范围以实现指南和验证记录为准。
> 目标：由SRM集成服务主动同步SAP主数据，为采购申请、订单和财务数据处理提供可追溯的本地数据副本。
> 架构：首次全量初始化，之后分页拉取增量，周期性执行全量对账；SAP负责数据和增量查询能力，SRM负责同步编排与消费进度。
> 技术基线：Java 17、项目父POM当前的Spring Boot 3.0.7、MyBatis-Plus、PostgreSQL；不根据README中的其他版本升级依赖。
> 实施约束：本轮只形成文档；后续执行任何测试、联调或压测前先取得用户批准。本文的验收案例均为计划。
> 本次补充：对齐数据同步九阶段学习笔记，补充增量范围、分页游标、批量写入和告警设计；CDC＋MQ、Canal保留为后续独立技术栈练习，不纳入当前交付。
> 实现进度：稳定游标主线、前端同步中心及基础调度已实现，新增功能经用户批准完成接口和浏览器联调。具体范围、Spring JDBC写入方式和启动演示见[实现与演示指南](./01-主数据同步实现与演示指南.md)，实际执行结果见[验证记录](./01-主数据同步验证记录.md)。本文仍包含可选设计，不能把整篇当作完成清单。

## 1. 客户背景与具体需求

以下是根据实习代码整理的复现场景，不额外声称为已核实的客户生产需求。

集团在SAP中维护成本中心，财务人员负责新增、变更名称、调整有效期及冻结状态；采购人员在SRM中提交采购申请和订单，必须选择合法成本中心。SAP同时维护企业认可的会计汇率，SRM需要据此生成外币采购的本币金额快照。

例如财务在SAP中新建“研发二部”，采购员随后需要为其提交设备采购申请。若SRM没有同步，用户无法选择该成本中心；若已停用数据没有同步，用户可能引用不再允许记账的成本中心。外币订单则需要明确采用哪个汇率类型、哪一天的汇率，不能在查询历史订单时套用最新汇率。

客户要求：

- 成本中心在约定同步周期内可用，变化不要求秒级生效。
- 同时同步新增、修改、冻结和停用；不把“只查新建记录”称为完整增量同步。
- SAP短暂不可用时，保留本地数据并提示数据新鲜度，任务可恢复。
- 同一数据反复同步不产生重复行，旧版本不能覆盖新版本。
- 查看任务进度、错误明细、数据来源和最后成功时间。
- 采购业务引用本地主数据；历史订单保留成本中心、汇率的业务快照。

本方案先约定成本中心每10分钟同步一次、汇率每日02:10同步，时区Asia/Shanghai。它们是练习配置，可按客户SLA调整，并非实习系统已核实的生产频率。

## 2. 已有代码与本次新增能力

实习源码根目录为 `E:/Intern_workspace_26y/cosmic_project/project4demo/code/esc/sshh-pro44-esc-app44/src/main/java/sshh/pro44/esc/app44/`。

| 依据 | 已观察到的行为 | 复现需要补充的能力 |
| --- | --- | --- |
| `utils/CostCenterUtil.java` | JCo调用SAP自定义RFC `ZFFI68` | 标准化DTO、超时、分页/变更契约 |
| `plugin/schedule/CostCenterSyncTask.java` | 拉取成本中心、转换后调用苍穹保存接口 | 可靠增量、检查点、版本保护、重试 |
| `utils/ExchangeRateUtil.java` | 调用自定义RFC `ZFF122` | 汇率单位、方向及比例的明确映射 |
| `plugin/schedule/ExchangeRateSyncTask.java` | 根据本地有效日期组织后续查询 | 支持历史汇率修订，不以最大生效日代替消费游标 |

上述RFC是该项目的自定义接口名，不能推广为所有SAP系统都存在的标准接口。`ERSDA`字段不能未经SAP方确认就当作最后修改时间。

当前工程的 `integration-service` 主要是骨架，尚未具备本方案的持久化同步引擎。原有《数据同步技术实现方案.md》保留不变；实际复现本功能时以本文约定为准。原文中的“CDC等于强一致”“自增业务ID能捕获修改”等泛化表述不作为本方案前提。

## 3. 系统责任与选型解释

| 参与方 | 职责 |
| --- | --- |
| SAP或本地SAP模拟服务 | 维护权威主数据；提供完整、可重放的变更流或查询接口；说明停用与删除语义 |
| SRM的integration-service | 调度、读取游标、分页调用、映射、校验、Upsert、检查点、日志、重试和对账 |
| procurement-service | 通过集成服务API使用主数据，不直连其schema；生成订单时固化所用值 |
| 运维人员 | 配置同步频率、处理失败批次，审批高风险人工修复 |

选择拉模式的原因是数据低频变化、可接受分钟级延迟，而且消费方需要独立控制流量和恢复。SAP提供增量查询接口只是上游的数据访问能力；SRM仍然需要完成端到端的数据同步开发。SRM在采购领域是主系统，但SAP在成本中心领域是权威源，不能用“我是主系统”作为选型理由。

拉取与全量/增量是两个维度。增量拉取的流量取决于变化量，而不是全表大小；分页还限制单次内存和响应大小。若上游只有全量接口，就采用定时全量加哈希比较，并如实承认网络传输仍为全量。

企业系统原则上通过RFC、API或集成平台交换数据，数据库分别管理。未经明确只读契约不查询SAP业务数据库。本项目模拟API与生产RFC分别实现同一适配器接口。

实时性提升后可演进为SAP事件推送加定期对账；当前不同时引入CDC和MQ实时主数据链路，以控制复现范围。

### 3.1 与九阶段学习路线的对应关系

学习笔记原案例是“订单数据库到分析仓库”，本项目是“SAP接口到SRM业务主数据”。九个阶段用于理解不同问题及其解法，不要求一个小型同步功能同时引入所有技术。

| 学习阶段 | 当前方案的处理 | 首期范围 |
| --- | --- | --- |
| 1. 全量同步 | 首次完整初始化；同一逻辑快照分页读取，并与增量位置接续 | 实施，见第8节 |
| 2. 增量同步 | 有可靠变更游标时优先采用；只有更新时间时用固定时间窗口 | 实施一种与上游匹配的模式，见第4节 |
| 3. 分批处理 | SAP每页返回有限记录，SRM逐页处理，失败按页回滚 | 实施，默认500条/页 |
| 4. 游标分页与断点续传 | 不用动态OFFSET；保存成功页位置，区分跨轮水位与页内游标 | 实施，见第4、7节 |
| 5. 性能优化 | 批量Upsert、限制单页与SQL大小、合理索引；同一数据流顺序处理 | 批量写入实施，分片多线程后置 |
| 6. CDC＋消息队列 | 源数据库日志捕获，再异步更新目标副本 | 后续独立练习，当前不部署 |
| 7. 可靠性保障 | 业务键、源版本、检查点、页事务、重试、租约、告警 | 当前实施接口同步需要的部分；消息有序和ACK留到CDC练习 |
| 8. 企业级工具 | DataX、Canal、Debezium按批处理或源数据库类型选择 | 当前不接入；Canal作为后续明确学习项 |
| 9. 数据对账 | 周期完整快照比对、差异报告、受版本约束的补偿 | 当前实施，不等到实时同步以后才做 |

全量失败不必等第二天才发现：日志、运行状态、重试和告警决定发现速度，与全量还是增量无直接绑定。CDC也不天然提供跨系统强一致或绝不丢失的保证。

### 3.2 首期固定主线与上游能力降级

本次补充保持已确认的首选：模拟SAP提供稳定变更游标，SRM顺序分页并批量写入。第4.2～4.6节的时间窗口方案用于解释学习笔记和适配其他接口，不要求首期同时实现两套同步引擎。

上线前根据SAP接口能力选择且记录模式：有稳定变更游标采用`SOURCE_CURSOR`；只有可靠更新时间采用`TIME_WINDOW`；仅有全量接口采用分页全量加业务键/哈希比较。三种模式的游标不能混用，切换须重新确认基线与增量接续位置。

## 4. 增量契约：先解决漏数，再写定时器

### 4.1 首选：源端稳定游标

模拟SAP实现追加式变更日志，成本中心新增、修改、冻结均生成新的完整状态事件。接口形态是本地复现契约，不宣称现有RFC已经支持。

```http
GET /mock/sap/v1/cost-centers/changes?after=cc:1000&limit=500
```

```json
{
  "items": [{
    "eventId": "cc-event-1001",
    "sourceVersion": 12,
    "controllingArea": "A100",
    "companyCode": "1000",
    "costCenterCode": "RD002",
    "name": "研发二部",
    "validFrom": "2026-01-01",
    "validTo": "2099-12-31",
    "blocked": false,
    "operation": "UPSERT",
    "changedAt": "2026-10-17T01:30:00Z"
  }],
  "nextCursor": "cc:1001",
  "hasMore": false
}
```

源端游标必须保证：已推进位置之前的已提交变化不会以后才出现；同一位置重读结果可解释；分页边界稳定；日志保留期明确；失效游标有明确错误。单纯数据库自增ID不等于提交顺序，不能只用 `max(id)` 越过尚未提交的小ID事务。

本地模拟服务可用一行源端序列控制记录，在业务数据变更事务内锁定、分配序号、写变更日志并提交，以序列锁串行化提交。实际SAP以其提供的变更文档、Delta能力或约定接口为准。

增量游标过期时停止推进并告警，进入重新全量初始化流程，不自动从当前最大位置继续。

### 4.2 备选：时间窗口

SAP只支持修改时间时，由源端返回本次上界 `upperBound`，固定窗口为 `[completedWatermark - overlap, upperBound)`，其中`completedWatermark`是上轮完整处理成功的源端时间水位，不是本地任务结束时间。分页按 `(changedAt, businessKey)` 排序并使用键集分页。同毫秒多条变化必须有第二排序键。

重叠窗口是降低迟到概率的工程措施，不能数学保证任意长事务、任意回填都不会漏。必须约定修改时间生成规则、最大迟到范围和定期全量校准；只有创建时间时不满足完整修改增量要求。

### 4.3 区分增量范围、页内游标和持久化检查点

| 概念 | 回答的问题 | 例子 |
| --- | --- | --- |
| 增量范围/跨轮水位 | 这一轮需要处理哪些变化 | 上轮完整成功到10:00，本轮处理到10:10 |
| 页内游标 | 本轮已读到哪一页 | 当前窗口已读到源记录ID=800 |
| 持久化检查点 | 故障恢复时从哪里继续 | 固定窗口及最后成功页位置，或者SAP的已提交变更游标 |
| 业务主键 | 哪些数据属于同一业务对象 | 控制范围＋成本中心编码 |
| 源版本 | 哪个对象状态更新 | 成本中心第12版，比第11版更新 |

例如源记录ID=42的成本中心已经同步，之后名称改变，ID仍然是42。若跨轮永远只查`id > lastId`，无法发现该修改。业务ID可以在固定窗口内分页，但不能单独识别修改增量。

反过来，同一成本中心每次修改都生成新的变更日志位置时，变更游标可以跨轮推进。业务ID与变更序号即使都是数字，也不具有相同语义。

### 4.4 按更新时间筛选，按ID分页：学习笔记的适用版本

在上游支持可靠更新时间、稳定且唯一的源记录ID时，可固定本轮窗口，再按ID分批。以下SQL应由模拟SAP或提供接口的上游执行，不是SRM直连SAP数据库。

```sql
-- 位于源端模拟器；本轮windowStart/windowEnd一经确定便固定。
select id, controlling_area, cost_center_code, name,
       updated_at, source_version, blocked, deleted
from mock_sap_schema.cost_center
where updated_at >= :windowStart
  and updated_at < :windowEnd
  and id > :lastPageId
order by id
limit :pageSize;
```

假设上轮完整水位为10:00，本轮源端上界为10:10，重叠5分钟，则读取`[09:55, 10:10)`。本页末尾ID=800成功落库后，把固定窗口和800一起保存。失败重启沿用原窗口，不能随当前时间扩大本轮上界。

本轮确认没有下一页后，把完整水位推进到10:10，并清空本轮页位置。下一轮重新按新窗口从最小ID开始，不能继续沿用800；这样小ID旧记录的后续修改才有机会进入新一轮。

此方案解决OFFSET因前面记录移出结果集而偏移的问题，不等于获得源端一致快照。分页时记录再次更新到本轮上界之后，由后续窗口获取；如果源端允许旧时间回填、超出重叠范围的迟提交，仍可能遗漏，必须依靠源端约定与周期对账兜底。

首期模拟源中的ID限定为正整数，首批起点为0；真实接口若不提供这种ID，则使用其业务复合键或不透明分页token，不能拿SRM本地自增ID代替源ID。

### 4.5 按更新时间与唯一键复合分页：同样有效的选项

不能把“不能按更新时间排序”当作通用规则。问题是排序不唯一、游标与排序不一致，或在变化的数据集上使用OFFSET。若源端有ID，可按`(updated_at, id)`唯一排序：

```sql
-- 第一页只使用固定窗口；后续页增加下面的复合游标条件。
select id, controlling_area, cost_center_code, name,
       updated_at, source_version, blocked, deleted
from mock_sap_schema.cost_center
where updated_at >= :windowStart
  and updated_at < :windowEnd
  and (updated_at, id) > (:lastPageUpdatedAt, :lastPageId)
order by updated_at, id
limit :pageSize;
```

同一毫秒有多条更新时，ID作为第二排序键，不能只写`updated_at > 上一页最后时间`。前述两种分页方案选一种，不在同一次任务中交替改变排序。

源端可按主要过滤和排序模式评估`(updated_at, id)`索引。第4.4节按ID排序的执行代价与其不同，不能保证同一索引对所有查询都最优，需源端执行计划确认后再调整。[PostgreSQL分页说明](https://www.postgresql.org/docs/current/queries-limit.html)强调分页应具有确定顺序，并说明大OFFSET仍需计算被跳过的行。

### 4.6 两种模式的检查点如何保存

现有`sync_checkpoint.committed_cursor`是text，首期约定保存带模式的JSON状态，不裸存一个意义不清的数字。稳定游标模式示例：

```json
{"mode":"SOURCE_CURSOR","sourceCursor":"cc:1001"}
```

时间窗口模式的页内恢复示例：

```json
{
  "mode": "TIME_WINDOW",
  "pagination": "UPDATED_AT_ID",
  "completedWatermark": "2026-10-17T02:00:00Z",
  "activeWindow": {
    "start": "2026-10-17T01:55:00Z",
    "end": "2026-10-17T02:10:00Z",
    "lastPageUpdatedAt": "2026-10-17T02:03:00Z",
    "lastPageId": 800
  }
}
```

如果采用第4.4节，`pagination`为`ID`，只使用`lastPageId`定位。`completedWatermark`只有整轮完成才更新，`activeWindow`每页成功就更新并与业务写入同事务提交；整轮结束时原子设置新水位并清空`activeWindow`。空窗口也要在正常结束后推进水位。

因此“每页提交”和“整轮推进时间水位”并不矛盾，更新的是不同状态。表中的`last_success_at`记录任务真正完成时间，不能用它替代源端完整水位。日志中的页号只供观测，恢复位置仍以检查点为准。

时间窗口练习仍由模拟源提供可靠的`source_version`，用于第6节版本保护。接入不提供对象版本的真实接口时，要另行确认更新时间是否足以比较新旧、以及如何隔离重放与实时写入；不能把业务ID或页号伪造为对象版本。

## 5. 业务键与汇率语义

成本中心首期按 `(controlling_area, cost_center_code)` 唯一，`company_code`作为业务属性。组织实例存在多个SAP逻辑系统时，再把`source_system`加入唯一键。此键需由SAP负责人确认，不能默认成本中心编码全集团唯一。原接口中尚未确认能否返回控制范围KOKRS：若每条连接固定一个范围，则由连接配置补齐；若一条接口覆盖多个范围，则要求SAP补充字段，不能凭公司代码推测。

首期只保存成本中心当前状态与有效期；若同编码存在多个有效期分段，需要追加有效期历史表，不能把不同期间覆盖成同一行。失效和删除事件保留本地记录并置不可用，不物理删除历史引用。

汇率业务键为 `(rate_type, from_currency, to_currency, effective_date)`。使用BigDecimal；保留源始汇率、源/目标换算因子和报价方式，转换为明确的“1单位原币可换多少目标币”。不得看到负号就简单取绝对值，也不能未经契约把UKURS和AKURS互为倒数。

同一生效日汇率可能后续修订，因此也要按变更时间/版本同步。新订单保存`rate_id/source_version/effective_date/normalized_rate`快照，历史订单不随主数据修订重算。

## 6. 数据模型与关键SQL

以下为拟新增结构，位于 `integration_schema`，由集成服务拥有。

```sql
create schema if not exists integration_schema;

create table integration_schema.cost_center (
  id bigserial primary key,
  controlling_area varchar(20) not null,
  cost_center_code varchar(40) not null,
  company_code varchar(20) not null,
  name varchar(200) not null,
  valid_from date not null,
  valid_to date not null,
  blocked boolean not null,
  deleted boolean not null default false,
  source_version bigint not null,
  source_changed_at timestamptz not null,
  synced_at timestamptz not null default clock_timestamp(),
  unique (controlling_area, cost_center_code),
  check (valid_from <= valid_to)
);

create table integration_schema.sync_checkpoint (
  job_code varchar(80) primary key,
  committed_cursor text,
  baseline_complete boolean not null default false,
  last_success_at timestamptz,
  version bigint not null default 0
);

create table integration_schema.sync_page_audit (
  id bigserial primary key,
  run_id uuid not null,
  page_no int not null,
  from_cursor text,
  to_cursor text,
  fetched_count int not null,
  applied_count int not null,
  ignored_count int not null,
  committed_at timestamptz not null default clock_timestamp(),
  unique (run_id, page_no)
);

create table integration_schema.sync_page_failure (
  id bigserial primary key,
  run_id uuid not null,
  attempt_no int not null,
  page_no int not null,
  from_cursor text,
  error_code varchar(60) not null,
  error_summary varchar(500) not null,
  sanitized_sample jsonb,
  occurred_at timestamptz not null default clock_timestamp()
);

create table integration_schema.sync_alert (
  id bigserial primary key,
  job_code varchar(80) not null,
  run_id uuid,
  category varchar(60) not null,
  severity varchar(20) not null,
  dedup_key varchar(180) not null unique,
  first_seen_at timestamptz not null default clock_timestamp(),
  last_seen_at timestamptz not null default clock_timestamp(),
  occurrence_count bigint not null default 1,
  status varchar(20) not null default 'OPEN',
  summary varchar(500) not null,
  check (status in ('OPEN','ACKNOWLEDGED','RESOLVED'))
);
```

汇率表首期结构如下；规范化值必须校验后写入，精度与取舍规则在实际SAP字段契约确认后固定：

```sql
create table integration_schema.exchange_rate (
  id bigserial primary key,
  rate_type varchar(20) not null,
  from_currency varchar(10) not null,
  to_currency varchar(10) not null,
  effective_date date not null,
  raw_rate numeric(28,12) not null,
  from_factor numeric(19,4) not null check (from_factor > 0),
  to_factor numeric(19,4) not null check (to_factor > 0),
  quotation_method varchar(20) not null,
  normalized_rate numeric(28,12) not null check (normalized_rate > 0),
  source_version bigint not null,
  source_changed_at timestamptz not null,
  synced_at timestamptz not null default clock_timestamp(),
  unique (rate_type, from_currency, to_currency, effective_date)
);
```

```sql
insert into integration_schema.cost_center
  (controlling_area, cost_center_code, company_code, name,
   valid_from, valid_to, blocked, deleted, source_version, source_changed_at)
values (:area, :code, :company, :name,
        :validFrom, :validTo, :blocked, :deleted, :sourceVersion, :changedAt)
on conflict (controlling_area, cost_center_code) do update
set company_code = excluded.company_code,
    name = excluded.name,
    valid_from = excluded.valid_from,
    valid_to = excluded.valid_to,
    blocked = excluded.blocked,
    deleted = excluded.deleted,
    source_version = excluded.source_version,
    source_changed_at = excluded.source_changed_at,
    synced_at = clock_timestamp()
where integration_schema.cost_center.source_version < excluded.source_version;
```

只有源端确实提供每个业务实体单调版本时，才采用上述版本比较。相同版本却不同内容视为上游契约异常并告警。批量同一SQL中出现多个相同业务键时，先按版本归并，避免同一目标行被多次冲突更新。

### 6.1 批量Upsert的具体方式

上面的单行SQL用于说明冲突与版本语义，实际写入首期采用MyBatis XML的`foreach`生成多VALUES语句，并保留相同的`ON CONFLICT ... WHERE source_version`条件。不要在Java循环里为每条记录各发送一条SQL。

```xml
<insert id="upsertCostCenters">
  insert into integration_schema.cost_center
    (controlling_area, cost_center_code, company_code, name,
     valid_from, valid_to, blocked, deleted, source_version, source_changed_at)
  values
  <foreach collection="records" item="r" separator=",">
    (#{r.controllingArea}, #{r.costCenterCode}, #{r.companyCode}, #{r.name},
     #{r.validFrom}, #{r.validTo}, #{r.blocked}, #{r.deleted},
     #{r.sourceVersion}, #{r.sourceChangedAt})
  </foreach>
  on conflict (controlling_area, cost_center_code) do update
  set company_code = excluded.company_code,
      name = excluded.name,
      valid_from = excluded.valid_from,
      valid_to = excluded.valid_to,
      blocked = excluded.blocked,
      deleted = excluded.deleted,
      source_version = excluded.source_version,
      source_changed_at = excluded.source_changed_at,
      synced_at = clock_timestamp()
  where integration_schema.cost_center.source_version &lt; excluded.source_version
</insert>
```

Mapper参数使用`@Param("records") List<CostCenterRow>`，`CostCenterRow`包含示例引用的十个属性。日期用LocalDate、源变更时间用Instant、版本用long；`records`为空时直接返回，不执行无VALUES的SQL。DAO返回影响行数，成功应用数依数据库真实结果统计，不把收到的条数都算成新增。

初始参数：源端每页500条，目标每条SQL最多200条，因此一页分200/200/100三次写入；同一页的全部SQL、页审计和检查点仍在同一个事务中。第三次写入失败，前两次也回滚，不能先提交目标记录再独立提交游标。

页内有同业务键多次变更时先按源版本归并。统计分别记录`fetched_count`、归并条数、实际应用数、重复/旧版本忽略数，不把归并后不存在的行计为失败。

### 6.2 分页、内存与性能优化边界

| 参数/措施 | 初始策略 | 目的 |
| --- | --- | --- |
| 上游pageSize | 500，可配置 | 限制接口响应和单页内存 |
| 目标SQL批量大小 | 200，可配置 | 减少往返，同时限制SQL和绑定参数规模 |
| 同一数据流并发 | 1，顺序分页 | 保持成功进度连续，简化恢复 |
| 不同数据流 | 成本中心、汇率可有限并行 | 使用独立检查点与资源租约 |
| 数据库唯一键 | 与Upsert业务键一致 | 保证幂等和冲突定位 |
| 源端索引 | 根据其过滤/排序与执行计划确定 | 避免每页全表扫描 |

分页必须在源端查询、返回之前生效。SAP一次返回十万条后，SRM再切成每组500条，只降低写库批量，不能避免全量接收时的内存和网络开销。若真实RFC没有分页能力，需要推动接口改造或承认当前是全量获取，不在文档中伪称已经解决上游分页。

首期不积攒所有页再写库，也不使用无界线程池或同时缓存所有响应。内存控制在当前页及必要工作副本，不随全量记录数线性累积。

批量写入主要减少SRM到数据库的通信次数，并不减少SAP需要返回的变化条数；增量识别、字段裁剪和压缩才有助于减少上游网络传输。

多线程留到有证据的瓶颈出现后评估。若第1、3批成功而第2批失败，不能把全局检查点推进到第3批末尾；需维护连续成功位置，或对互不重叠分片建立独立检查点。小型主数据首期不承担这类复杂度。

## 7. 同步执行与事务边界

```text
领取任务租约 → 读取检查点 → SAP拉一页（事务外）
  → 映射/校验 → 打开短事务
  → 校验当前租约token → 批量Upsert
  → 写页审计 → 更新本页已提交游标 → 提交
  → 拉下一页 → 全部完成后更新last_success_at
```

稳定变更游标模式的检查点采用“每页提交”，不把整个任务放入一个长事务。某页写入失败，该页的数据、审计和游标一起回滚；前面已成功页保留，下次从最后成功页继续。时间窗口模式同时保存整轮完整水位和页内进度，按第4.6节提交；不能让失败页之后的水位提前生效。单页默认500条，大小可配置。

同步任务不在网络请求期间持有数据库长事务。页落库方法放在独立Spring Bean并加事务，避免同类自调用导致事务注解失效。任务租约与旧实例隔离由第02份文档提供。

数据不合法时首期采用整页失败并保存脱敏错误样本，不跳过坏数据后悄悄推进游标。修复映射或源数据后重放；若以后支持隔离坏数据继续推进，必须先把原始记录可靠持久化并形成待补偿义务。

### 7.1 失败发现、告警与补偿入口

同步失败要及时发现，不等待下一次定时触发。运行状态与自动重试沿用02中的`job_run`，`sync_page_audit`只记录成功页；失败页号、起始位置、错误分类和脱敏样本写入`sync_page_failure`并关联run。错误记录在失败页事务回滚后，以独立短事务保存，避免和业务数据一起被回滚；数据库整体不可用时先输出结构化错误日志，恢复后由运行恢复扫描补充告警。任务级统计由已提交页审计聚合，避免失败重试导致统计重复累加。

| 情况 | 运行处理 | 告警规则与后续动作 |
| --- | --- | --- |
| 网络超时、429、短暂5xx | 保留检查点，进入RETRY_WAIT，按02退避 | 记录重试；连续3次仍失败产生一次告警 |
| 认证失败、字段映射错误、游标过期 | 停止普通自动重试，记录明确错误 | 立即生成运维待办，修复后重跑或重建基线 |
| 运行失联或租约过期 | 按02恢复扫描接管 | 超过恢复阈值产生告警，旧实例禁止写入 |
| 成本中心完整同步超过30分钟未成功 | 不因任务“正在重试”消除滞后事实 | 产生数据新鲜度告警 |
| 汇率到Asia/Shanghai每日03:00仍未完成当日任务 | 保留原汇率，不把旧值标为当日新值 | 产生当日失败/滞后告警 |
| 全量对账有差异 | 保存差异报告 | 提示管理员核对，不无条件删除或覆盖 |

阈值均为练习初始配置，不代表真实客户SLA。持久化新增`integration_schema.sync_alert`，至少包含id、job_code、run_id、category、severity、dedup_key、first_seen_at、last_seen_at、occurrence_count、status、summary，`dedup_key`唯一。按任务和告警类别聚合未恢复事件，避免每次扫描或每页失败都重复创建待办。

去重键首期使用`jobCode:category`；未恢复时更新last_seen_at并累加次数。已RESOLVED后故障再次发生时重新置OPEN、重置本次发生区间与次数，详细历史保留在运行和失败页记录中。ACKNOWLEDGED只代表已读，不影响滞后扫描或自动恢复判定。

首期必须实现数据库告警待办及任务页面红色状态；邮件通过`AlertNotifier`作为可选适配器，配置SMTP后再启用，未配置时明确显示“邮件未启用”。如启用邮件，记录发送状态与重试，不能将邮件发送失败解释为业务页事务失败，也不在持有业务事务时发送邮件。

人工补偿入口复用`POST /api/integration/job-runs/{runId}/retry`，记录操作者与原因，从最后已提交页恢复；历史重放不回退普通检查点。后续任务追赶成功后将对应告警置RESOLVED，保留历史，不能仅因用户点击“已读”就标记恢复。

`sync_alert`随本功能迁移创建，告警产生与run状态维护对接02；告警服务仅负责运行可见性，不替代同步幂等和错误恢复。

## 8. 全量初始化与对账

完整初始化需要SAP提供一致快照及与其对应的增量位置`C0`，或者先记录可重放位置再读全量，之后重放这期间所有变化。不能先读全量、再随手记录当前最大游标，否则窗口内变化会丢失。

首期模拟接口提供 `snapshotId + baselineCursor`，分页结果属于同一个逻辑快照。初始化完成后从`baselineCursor`回放增量；未完成前不把主数据标记为可用于正式业务。

每周日03:00执行快照对账，按标准字段规范化后计算SHA-256并按业务键比较，整体复杂度接近O(N)，不是必然O(N²)。对账结果区分缺失、内容差异和疑似本地多余。

只有全量快照确实完成、过滤范围一致且源端明确支持“缺失等于删除”时，才能把多余记录标停用；接口少一页、权限变更或源端超时不能触发批量删除。补偿也要遵守源版本保护，不能用旧快照覆盖新增量。

## 9. 接口与页面

| 接口 | 作用 |
| --- | --- |
| `GET /api/integration/cost-centers` | 按公司、关键字、有效日期查询；返回最后同步时间 |
| `GET /api/integration/exchange-rates/resolve` | 按币种、类型、业务日期解析汇率 |
| `POST /api/integration/jobs/{jobCode}/runs` | 创建异步执行请求，返回202和runId，详见02 |
| `GET /api/integration/jobs/{jobCode}/checkpoint` | 只读查看成功游标、初始化状态 |

业务方不能任意设置游标跳过数据。补偿操作要求管理员权限、原因和审计；模拟SAP管理接口仅在`mock-sap`配置启用。

主数据过期时页面显示新鲜度。停用或已知不合法成本中心禁止新订单引用；同步滞后超过SLA时是否阻断采购提交应配置为明确客户策略，不能静默无限使用旧数据。

## 10. 当前项目落点与实施清单

相对项目根目录的代码路径（按2026-10-18分层重构更新）：

| 路径 | 职责 |
| --- | --- |
| `backend/integration-service/pom.xml` | 补充Validation、JDBC/MyBatis-Plus、PostgreSQL、Flyway；沿用父POM版本 |
| `backend/integration-service/src/main/java/com/example/procurement/integration/service/impl/` | 同步流程、页写入、对账、任务管理、租约与告警Service实现 |
| `backend/integration-service/src/main/java/com/example/procurement/integration/dao/` | 数据库DO、MyBatis Mapper和UUID类型处理器 |
| `backend/integration-service/src/main/resources/mapper/integration/CostCenterMapper.xml` | 多VALUES批量Upsert；汇率按其业务键定义独立Mapper |
| `backend/integration-service/src/main/java/com/example/procurement/integration/client/` | `SapMasterDataClient` HTTP客户端；真实SAP适配仍需另行实现 |
| `backend/integration-service/src/main/java/com/example/procurement/integration/mock/` | 独立数据源的SAP模拟业务、DAO与资源生命周期 |
| `backend/integration-service/src/main/java/com/example/procurement/integration/dto/`、`domain/` | 请求/响应/外围传输DTO；运行上下文、枚举和内部结果 |
| `backend/integration-service/src/main/resources/db/integration/migration/V1__master_data_sync.sql` | 主数据、检查点、成功页审计、sync_page_failure与sync_alert；任务表由02定义 |
| `backend/integration-service/src/main/java/com/example/procurement/integration/config/IntegrationMybatisConfiguration.java` | 聚合与独立模式共用integration Mapper扫描和UUID注册 |
| `backend/aggregation-service/src/main/java/com/example/procurement/aggregation/AggregationServiceApplication.java` | 保留integration独立Flyway位置 |
| `web/src/views/integration/` | 主数据列表及任务详情入口 |

模拟器使用独立`mock_sap_schema`及独立数据源/账号，模拟接口负责源端变更；SRM同步代码禁止直查其表。无真实JCo运行环境时以HTTP模拟联调，不把模拟RFC调用写成真实SAP联通。

- [ ] 建源端模拟数据和变更契约：新增、修改、停用均进入日志。
- [ ] 建目标表、业务键与页事务，先支持手动单次执行。
- [ ] 完成真实多VALUES批量Upsert：默认每页500条、每SQL200条，整页同事务。
- [ ] 完成全量基线与增量接续，返回逐页处理统计。
- [ ] 接入02中的调度、租约、恢复与运行日志。
- [ ] 实现失败明细、去重告警待办与人工补偿入口，邮件按配置选配。
- [ ] 增加汇率规范化与订单快照读取接口。
- [ ] 实现全量差异报告和管理员补偿。

建议先完成稳定游标主线，再用可替换适配器练习固定时间窗口；后者应独立配置与验收，不在运行中混用两种检查点。CDC与Canal不在上述首期清单内。

## 11. 待批准的验收场景与指标

| 场景 | 预期 |
| --- | --- |
| 首次全量期间源数据变化 | 后续重放覆盖变化，最终与源一致 |
| 同一页重复执行 | 目标无重复，旧版本不覆盖新版本 |
| 第二页落库失败 | 第一页保留，第二页游标不推进 |
| 一页500条中第三个写入子批失败 | 整页三个子批和检查点一起回滚 |
| 源端分页返回超量或无法分页 | 明确报告接口契约不满足，不假装本地切List已解决OOM |
| 时间窗口练习中低ID记录再次修改 | 下一轮页游标重置后可读取，不能被旧最大ID排除 |
| 同毫秒多条变化跨页 | 唯一第二排序键使分页边界明确 |
| 时间窗口第2页失败后恢复 | 复用原固定上下界和最后成功页，不扩大窗口或提前推进完整水位 |
| 连续重试或滞后超过阈值 | 产生去重告警待办；追赶成功后解除并保留历史 |
| 同一生效日汇率修订 | 新查询可见新版本，历史订单快照不变 |
| 游标过期 | 任务失败并要求重建基线，不跳过缺口 |
| 全量缺页 | 不批量停用本地记录 |

记录吞吐条数/秒、每页写入耗时、端到端同步延迟、失败重试数、游标滞后、对账差异数。任何性能数字待实际运行后填写。

获批后比较单条写入与批量写入时，使用同一数据集、相同索引和环境，同时记录SQL执行次数、数据库写入耗时与端到端耗时。默认参数不是性能结论；若瓶颈在SAP响应，目标写入提速不一定同比提升整体同步速度。

## 12. 后续独立技术栈练习：CDC＋MQ与Canal

### 12.1 与当前业务的边界

用户已明确把CDC＋MQ和Canal放到后面，作为技术栈练习。当前成本中心、汇率仍走SAP接口拉取；后续练习采用可控数据库和独立目标投影，不假设可以订阅真实SAP底层日志。

后续场景可设为“采购订单状态准实时同步到分析看板”，学习源数据库变更、日志位点、消息交付与投影恢复。不要把分析投影当成订单业务权威数据，也不把个人实验描述成SAP生产CDC接入。

### 12.2 两条可选实验路线

| 路线 | 源与链路 | 练习范围 |
| --- | --- | --- |
| PostgreSQL路线 | 可控PostgreSQL → Debezium → 消息通道 → 独立分析投影 | 利用现有数据库技术，学习快照、日志位点和变化消费 |
| Canal路线 | 新增独立MySQL实验库 → Canal → 桥接投递器 → RabbitMQ → 独立分析投影 | 专门学习MySQL binlog与Canal；桥接器负责可靠发布与位点确认 |

Canal的主要能力是MySQL binlog订阅，不把它当作当前PostgreSQL数据库的直接CDC组件。参考[Canal官方项目](https://github.com/alibaba/canal)。PostgreSQL可选择有相应连接器的方案，参考[Debezium PostgreSQL连接器](https://debezium.io/documentation/reference/stable/connectors/postgresql.html)。

两条都是后续选项，不要求同时部署。不更换当前SRM数据库以迁就Canal，也不提前安装MySQL、Canal或Kafka。消息通道和桥接方式在开启练习时单独确定；使用现有RabbitMQ并不意味着任意CDC工具都能原生直投，不能省略适配与确认协议。

DataX可作为另一个离线全量迁移/对比实验，和CDC工具不是同一种定位；首期不接入。参考[DataX官方项目](https://github.com/alibaba/DataX)。

### 12.3 后续练习的可靠性目标

| 学习问题 | 需要完成的设计 |
| --- | --- |
| 初始数据与增量接续 | 明确快照边界和日志位点，避免快照期间变化遗漏 |
| 重复消息 | 事件身份和业务键幂等，允许至少一次投递 |
| 同一订单状态乱序 | 按业务键有序处理，或使用可比较源位置拒绝旧状态 |
| 删除、DDL变化 | 明确删除事件、字段变更和不兼容schema的处理 |
| 桥接器故障 | 位点确认与MQ发布协调，发布结果不确定时允许安全重放 |
| 消息积压 | 监控最老事件年龄、队列长度和消费速率；先找瓶颈再扩容 |
| 故障恢复 | 验证捕获器、桥接器和消费者重启后的持久化进度 |
| 数据对账 | 对源与分析投影定期比对，修复过程仍受新旧版本约束 |

编号本身不等于幂等，分区本身不等于所有数据全局有序，集群扩容也不自动消除丢失和重复。实时链路仍采用独立投影与定期对账，不把CDC或MQ描述为自动保证Exactly Once。

后续练习完成并获得实际验证后，才在技术栈或简历中增加CDC、Canal等内容。本文仅保留学习路线，首期运行和验收不依赖它们。

## 13. 面试讲解与简历口径

业务先讲：“SAP维护成本中心和汇率，SRM采购人员需要用这些数据做采购归属和外币金额计算；我们需要同步本地副本，避免每次操作依赖SAP在线。”

架构再讲：“SAP提供查询变化的能力，SRM负责调度和成功消费进度。因为成本中心低频变化且允许分钟级延迟，选择首次全量、后续增量拉取，并周期对账。每页写入与游标提交在同一事务，重复处理通过业务键和源版本保护。”

学习路线可补充：“我先解决全量初始化，再处理修改增量、分页内存和断点恢复，随后用批量写入减少数据库往返。当前数据规模下同一数据流顺序处理即可；CDC和Canal留作订单分析投影的独立练习，不把工具堆叠当作选型理由。”

完成复现后可写：“实现SRM侧SAP主数据同步，采用分页增量拉取、批量Upsert与页级检查点，支持成本中心停用、汇率修订和周期对账。”实习原实现与个人工程增强分开陈述。

## 14. 关联文档

- [02-分布式调度与失败补偿技术方案](./02-分布式调度与失败补偿技术方案.md)：统一触发、租约和任务恢复。
- [03-BPA合同额度并发控制技术方案](./03-BPA合同额度并发控制技术方案.md)：订单使用主数据与价格快照的业务背景。
