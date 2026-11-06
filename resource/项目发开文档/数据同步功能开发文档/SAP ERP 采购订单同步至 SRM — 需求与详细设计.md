# SAP ERP 采购订单同步至 SRM — 需求与详细设计

状态：待确认的编码基线。日增量、调度周期和可见时限为本项目设计参数。

## 1. 业务需求与范围

芯恩晶圆制造场景中，以设备备件采购为例。ERP 提出采购需求，SRM 完成询报价与定标，将中选供应商及议定价格等结果回传 ERP。ERP 创建、审批并对外发布采购订单，供应商在 SRM 查看正式订单、确认数量和交期，或提出异议。采购员接受调整后在 ERP 修改订单，符合发布条件的新版再进入 SRM。供应商确认后可进入后续物流协同。 [业务背景](https://www.cnblogs.com/xihong2014/p/14397884.html) · [SAP 寻源定标集成](https://help.sap.com/docs/ARIBA_SOURCING/f2ab1e5180264bf686bcef6de607e263/rfq-and-award-integration-with-sap-ariba-sourcing?locale=en-us) · [芯恩官网](https://www.xinen.com/)

本期实现 **ERP 已发布订单头与订单行及后续变更 → SRM 正式采购订单**，以及同步运维。请购、寻源、ERP 审批是上游背景。供应商门户登录、订单确认和异议、物流、收货及开票属于后续业务。

| 业务事件 | SRM 行为 |
| --- | --- |
| 首次发布 | 原子写入 SRM 订单头与明细，供下游业务使用 |
| 修改数量、单价、交期后发布 | 更新当前发布版本，保留 SRM 自己维护的流程字段 |
| 已发布订单行取消或整单撤回、取消 | 同步撤回或取消状态，保留业务记录及既有引用 |

源系统按单一 ERP 实例接入，订单号和行号用字符串保存，保留前导零。首版订单发布后供应商固定，改派供应商通过撤销原单和发布新单处理。SRM 在现有采购订单业务表保存当前发布版（含取消状态），完整历史订单内容以 ERP 为准。暂定每天约 1,200 条订单行变化，每 5 分钟调度一次，发布到 SRM 可查目标为正常情况下 10 分钟内。验收分别统计平稳与集中发布批次，统计值和生产数据分开记录。

## 2. 系统职责与表归属

**ERP 管正式采购订单，SRM 直接用自己的采购订单业务表做订单协同。** ERP 审批发布后，SRM 将订单写入已有的 `procurement_schema.purchase_order` 和 `procurement_schema.po_line`。采购员与供应商查看同一份 SRM 订单。现有收货、服务确认及发票关系仍可引用这套订单头、订单行的本地 UUID。SRM 的订单表按来源区分本地订单和 ERP 订单；ERP 同步只维护 ERP 来源的记录。

ERP 模拟端用 `mock_sap_schema.purchase_order` 和 `mock_sap_schema.purchase_order_line` 两张业务表。接口把同一版订单头和全部明细组装成一份整单响应，数据库无需独立快照表。SAP 的采购订单 API 同样区分 Header 和 Item；本地字段与表名属于项目模拟模型。 [SAP 采购订单 API](https://help.sap.com/docs/SAP_S4HANA_CLOUD/0e602d466b99490187fcbb30d1dc897c/c89eec80ec2043d980cb7b8c89e0a00a.html)

本期由 XXL-JOB 统一触发同步、对账与补偿。采购订单模块不使用 Spring `@Scheduled` 或本地轮询执行器。

## 3. 库表设计

核心范围共 **15 张表**，新增 2 张 ERP 订单表，复用 2 张 SRM 订单表和 11 张运行表。已有供应商档案只作只读依赖，XXL-JOB Admin 自身的调度平台表独立管理，均不计入这 15 张表。

| 所属 | 表 | 用途 |
| --- | --- | --- |
| ERP 模拟端 | `mock_sap_schema.purchase_order`、`mock_sap_schema.purchase_order_line` | 新增正式订单业务头表与行表 |
| SRM 采购业务 | `procurement_schema.purchase_order`、`procurement_schema.po_line` | 复用正式业务表，补来源和发布字段 |
| 同步基础设施 | `integration_schema` 下 11 张既有运行表 | 调度、租约、检查点、运行、幂等、成功页、失败页、告警、对账观察、差异、管理审计 |

首版约定 ERP 供应商编码与现有 `supplier_schema.supplier.supplier_no` 一致，按编码批量查本地 `supplier_id`、`supplier_no` 和名称。不同编码的环境应接入已有映射。匹配不到时整页回滚并告警，主数据补齐后由 XXL-JOB 重跑。订单同步不创建或修改供应商档案。

### 3.1 ERP 模拟业务表

首版从正式发布动作开始模拟。待审批修改由 ERP 内部处理，发布前继续以原先对外版本供查询。演示接口一次保存正式发布的订单头和明细，并推进订单头时间。已发布的行取消用状态表达，保留行号和最后的业务值；订单撤回、取消同样继续向增量接口提供。

~~~sql
create table mock_sap_schema.purchase_order (
  id bigint generated always as identity primary key,
  order_no varchar(40) not null unique,
  title varchar(200) not null,
  supplier_code varchar(40) not null,
  purchasing_org varchar(32) not null,
  order_date date not null,
  currency varchar(8) not null,
  publication_status varchar(16) not null
    check (publication_status in ('PUBLISHED','WITHDRAWN','CANCELLED')),
  published_updated_at timestamptz(6) not null
);
create index idx_mock_po_published_cursor
  on mock_sap_schema.purchase_order(published_updated_at,id);

create table mock_sap_schema.purchase_order_line (
  order_id bigint not null references mock_sap_schema.purchase_order(id),
  line_no varchar(16) not null check (line_no ~ '^[0-9]{5}$'),
  material_code varchar(32) not null,
  material_name varchar(200) not null,
  quantity numeric(19,4) not null check (quantity > 0),
  unit varchar(16) not null,
  unit_price numeric(19,4) not null check (unit_price >= 0),
  amount numeric(19,4) not null check (amount >= 0),
  required_date date not null,
  line_status varchar(16) not null check (line_status in ('ACTIVE','CANCELLED')),
  primary key (order_id,line_no)
);
~~~

- `id` 在订单改期、改量时保持稳定，与 `published_updated_at` 组成增量游标。
- **任意一条明细的发布变更都在同一事务推进订单头的 `published_updated_at`**。同一订单的值严格递增，使用源库时钟与微秒精度。若时钟无法给出更晚值，拒绝此次更新并保留原版本。
- `line_no` 保留 5 位数字文本，支持 `00010`。演示金额由源端校验，币种继承订单头，订单金额按有效行金额求和。接口对数量、单价和金额使用十进制字符串。

### 3.2 SRM 现有订单头与订单行

| 既有表 | 保留并直接使用 | 增加字段 |
| --- | --- | --- |
| `procurement_schema.purchase_order` | `id` 本地 UUID，`po_no` SRM 全局唯一编号，`title`、`supplier_id`、`supplier_no`、`supplier_name`、`currency`、`total_amount`、`order_date`、`status`、`version` 及既有流程字段 | `source_system`、`source_id`、`source_order_no`、`source_supplier_code`、`source_purchasing_org`、`source_updated_at`、`source_publication_status`、`source_payload_hash`、`synced_at` |
| `procurement_schema.po_line` | `id` 本地行 UUID，`po_id` 实际外键，`line_no` 本地整数行号，`material_no`、`material_name`、`unit`、`quantity`、`unit_price`、`amount`、`required_date` 和 `received_quantity` 等 | `source_line_no` 保留源端原始行号，`line_status` 供业务展示及拦截取消行 |

`po_no` 继续保持全库唯一。ERP 单第一次导入时用本地序列生成 `EXT-<序号>`，后续不更改；页面可同时显示本地单号和 `source_order_no`。ERP 行按 `(po_id,source_line_no)` 匹配，保留已有本地 UUID，数字格式校验后写入 `line_no`。SRM 手工单来源字段为空，已有行默认有效。

~~~sql
alter table procurement_schema.purchase_order
  add column source_system varchar(32),
  add column source_id bigint,
  add column source_order_no varchar(40),
  add column source_supplier_code varchar(40),
  add column source_purchasing_org varchar(32),
  add column source_updated_at timestamptz(6),
  add column source_publication_status varchar(16),
  add column source_payload_hash char(64),
  add column synced_at timestamptz(6),
  add constraint ck_po_source_complete check (
    (source_system is null and source_id is null and source_order_no is null
      and source_supplier_code is null and source_purchasing_org is null and source_updated_at is null
      and source_publication_status is null and source_payload_hash is null and synced_at is null)
    or
    (source_system is not null and source_id is not null and source_id > 0
      and source_order_no is not null and source_supplier_code is not null and source_purchasing_org is not null
      and source_updated_at is not null and source_publication_status is not null
      and source_publication_status in ('PUBLISHED','WITHDRAWN','CANCELLED')
      and source_payload_hash is not null and synced_at is not null)
  );
create sequence procurement_schema.external_po_no_seq;
create unique index uk_po_source_id
  on procurement_schema.purchase_order(source_system,source_id)
  where source_system is not null;
create unique index uk_po_source_no
  on procurement_schema.purchase_order(source_system,source_order_no)
  where source_system is not null;
create index idx_po_supplier_source_version
  on procurement_schema.purchase_order(supplier_id,source_updated_at desc,id desc)
  where source_system is not null;

alter table procurement_schema.po_line
  add column source_line_no varchar(16),
  add column line_status varchar(16) not null default 'ACTIVE'
    check (line_status in ('ACTIVE','CANCELLED'));
create unique index uk_po_source_line
  on procurement_schema.po_line(po_id,source_line_no)
  where source_line_no is not null;
~~~

**ERP 同步维护**外部订单号、来源、正式订单条款、发布状态及订单行状态。**SRM 业务维护**本地编号、UUID、流程状态和收货量。首次导入将现有 `status` 初始化为 `SENT`，后续同步只更新 ERP 管理的字段。下游使用订单时根据 `source_publication_status` 和 `line_status` 判断可用性。撤回和取消保留本地订单、行 UUID 和已有收货等事实。

### 3.3 核心业务关系

~~~mermaid
erDiagram
    MOCK_PURCHASE_ORDER ||--|{ MOCK_PURCHASE_ORDER_LINE : "contains"
    MOCK_PURCHASE_ORDER ||..o| SRM_PURCHASE_ORDER : "synchronizes"
    SRM_PURCHASE_ORDER ||--|{ SRM_PO_LINE : "contains"
~~~

ERP 和 SRM 通过来源键关联，属于应用关系。两端订单头和订单行各自通过本地外键关联。

### 3.4 同步运行表

| 复用表 | 核心字段与用途 |
| --- | --- |
| `job_definition`、`job_lease` | `job_code` 为 `SAP_PURCHASE_ORDER_DELTA`；共享 `resource_key=SAP_PURCHASE_ORDER_WRITER`；租约与 `fence_token` 防止旧执行器回写 |
| `sync_checkpoint` | `baseline_complete` 标识基线完成；`committed_cursor` 保存已提交进度；`last_success_at` 记录最近完整成功 |
| `job_run`、`job_request_key` | `run_id` 串联运行、重试与审计；按 XXL 调度身份去重，复用幂等表保存请求到运行的映射 |
| `sync_page_audit`、`sync_page_failure`、`sync_alert` | 成功页、失败信息及告警聚合 |
| `sync_reconciliation_seen`、`sync_reconciliation_diff` | 已见订单、源目标版本与差异复核、修复结果 |
| `management_audit` | 手动触发、补偿、修复、任务开关的操作者与理由 |

## 4. ERP 接口与同步算法

| 接口（模拟前缀 `/internal/mock-sap/purchase-orders`） | 语义 |
| --- | --- |
| `GET /anchor` | 源端时间与当前最大订单头 ID |
| `GET /baseline` | 按 `afterId < id <= maxId` 分页读取订单头与全部行 |
| `GET /changes` | 窗口 `[startAt,endAt)`，按 `(published_updated_at,id)` 分页，游标大于上页最后一单 |
| `GET /{sourceId}` | 点查最新发布内容及状态，供对账与补拉 |
| `POST /publish` | 演示发布、改量、改期、撤回和取消；已有订单校验期望版本；订单头与行同事务写入 |

源端**先分页订单头，再批量查该页订单 ID 对应的明细**。两次查询必须使用同一个只读一致性快照（或一个 SQL），避免 ERP 正好发布新版本时返回旧头、新明细。单页限制订单数和字节数，单张订单完整返回。SRM 收到数据后按来源键批量 Upsert 已接受版本的头与行，目标页事务将订单、页审计与检查点一起提交。目标页事务只访问 SRM PostgreSQL，HTTP 请求在事务外完成。 [PostgreSQL 事务隔离](https://www.postgresql.org/docs/16/transaction-iso.html)

同一订单时间更大时应用，较旧时跳过，同时间同哈希幂等跳过，同时间不同哈希作为契约冲突告警。哈希只覆盖 ERP 管理的规范化业务内容及明细，排除 SRM 本地订单号和收货量。源端应明确标记取消行；缺少此前已知的行视为响应不完整，不推断取消。批量落库更新行时按稳定键查找，不删除已有行、不覆写本地收货字段。

### 4.1 简化检查点

日常增量的 `committed_cursor` 只保存 **`completedWatermark`、`windowEndAt`、`pageCursor`**。游标含 `updatedAt` 与 `sourceId`。窗口开始按 `completedWatermark - 回扫时长` 计算；未完成窗口期间回扫配置保持固定。`windowEndAt=null` 表示当前无待恢复窗口，运行追踪使用 `job_run.run_id`。页游标随每页提交；整个窗口完成才推进水位并清空活动字段。

增量窗口的上界直接取本轮取得的**源端当前时间** `sourceNow`，不扣安全延迟。查询区间为 `[completedWatermark - overlap, sourceNow)`。上界固定写入 `windowEndAt`，重试沿用该值。源时间未超过完成水位时跳过新窗口，发现时钟回退时告警。默认每 5 分钟调度，回扫初值 5 分钟。源事务若在窗口扫描后才提交，短时延迟可以由下一轮回扫覆盖，超过回扫范围的差异由对账发现。

首次全量取 `T0` 和固定 `maxId`，本次运行内顺序分页。失败后重新取锚点从头扫，依靠唯一键和版本约束去重。全量完成后以 `T0` 初始化完成水位，再用重叠窗口衔接增量。

## 5. XXL-JOB 调度、对账与验收

| 任务 | 触发方式 | 执行内容 |
| --- | --- | --- |
| `sapPurchaseOrderSyncJobHandler` | XXL-JOB Admin 每 5 分钟触发，也可在 Admin 手动执行 | 首次全量或增量。失败回传失败结果，由 Admin 配置有限次数重试 |
| `sapPurchaseOrderReconcileJobHandler` | XXL-JOB Admin 低峰定时或手动执行 | 分页只读对账，记录差异 |
| `sapPurchaseOrderRepairJobHandler` | 授权人员在 XXL-JOB Admin 手动触发，参数指定差异或订单 ID | 点查 ERP 最新状态并修复指定差异，保留审计 |

同一次 XXL 投递按任务和调度身份去重。XXL 新一次执行从同一任务的已提交检查点恢复，相同投递再次到达时复用原运行结果。同步、对账和修复按同一数据流租约串行。执行器按页续约并在提交前校验围栏令牌，失联后由后续 XXL 执行识别过期租约并恢复。运行页面查询进度、日志和差异，触发入口统一在 XXL-JOB Admin。

- **对账。** 固定上界取源端最大 ID 和本地已同步最大源 ID 的较大值，比较按实际头、行重算的内容哈希。缺单或不一致先源端点查。单次 404 只报告，取消依据明确状态。修复不推进增量水位。
- **监控。** 记录运行 ID、页游标、拉取和应用数量、耗时、延迟与错误码。供应商未匹配、版本冲突、连续失败和延迟超标生成告警。
- **验收。** 验证只改订单行也能命中增量、同时间戳分页不漏单、订单头行同版、取消保留记录、重试不覆写本地收货量。模拟失败后由 XXL 续跑，验证短时晚提交由重叠回扫补拉，超出回扫范围后对账能发现。

## 6. 实施与迁移边界

1. 聚合应用当前显式运行 SRM、STC、Integration 的迁移，采购订单和聚合供应商基础 DDL 位于另外的 `db/migration`。编码前先只读核实目标库基础表及 Flyway 历史，明确接入基础迁移和增量迁移的顺序；已有环境不得盲目重复执行旧建表脚本。完成 `procurement_schema` 扩展并确认既有供应商查询可用后，才允许启用集成任务。聚合运行时，业务订单写入、页审计和检查点使用同一个事务管理器和目标数据源。模拟 ERP 迁移独立执行。
2. 新增订单同步任务定义、空检查点和资源租约，默认禁用；在完成源数据、供应商映射和权限联调后启用。旧同步任务停止调度，历史记录保留。
3. 先交付两张源业务表、两张目标业务表的增量改造与 XXL-JOB 主流程，再接入对账、告警与运行页面。真实 SAP 接口与正式身份系统须核对授权、发布语义、可读字段及分页能力。测试和数据库迁移按项目要求另行批准后执行。
