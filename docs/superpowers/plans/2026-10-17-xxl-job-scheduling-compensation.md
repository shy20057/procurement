# XXL-JOB Distributed Scheduling and Failure Compensation Implementation Plan

> Historical implementation paths below were reorganized on 2026-10-18. Current structure and old-to-new mapping: `resource/项目发开文档/简历五大功能技术实现方案/00-集成模块分层与代码阅读指南.md`. The scheduling and consistency requirements remain applicable.

> **For agentic workers:** Implement this plan task-by-task in the current session. Do not delegate unless the user explicitly requests delegation. Do not run tests, builds, Docker startup, service startup, migrations, or integration verification until the user separately approves the specific verification step.

**Goal:** Replace the two SAP master-data business Cron triggers with XXL-JOB 2.4.2 while preserving SRM-side idempotency, resource leases, fencing tokens, checkpoints, manual execution, and the existing Vue-to-Gateway API path.

**Architecture:** XXL-JOB owns schedule time, routing, timeout, retry, and scheduling logs. PostgreSQL remains the source of truth for business runs and consistency. An XXL handler executes synchronously through a shared `JobExecutionCoordinator`; manual runs continue through the bounded local worker. Both paths contend for the same dataset-level lease, but only one retry system owns a failed run: XXL for `XXL` triggers and SRM persistent backoff for manual triggers.

**Tech Stack:** Java 17, Spring Boot 3.0.7, XXL-JOB 2.4.2, PostgreSQL 16, MySQL 8, Flyway, Vue 3, TypeScript, Element Plus, Docker Compose.

**Spec:** `resource/项目发开文档/简历五大功能技术实现方案/02-分布式调度与失败补偿技术方案.md`

## Global Constraints

- Keep Spring Boot at 3.0.7 and XXL-JOB at 2.4.2.
- Keep the frontend API base path `/api/integration`; switching between aggregation and microservice mode must not require frontend edits.
- Development mode manually starts only `aggregation-service` and `api-gateway`; do not also start `integration-service` because both would register the executor and compete for port 9999.
- Use a dedicated MySQL database for XXL-JOB Admin and PostgreSQL for SRM business state. Do not introduce a cross-database transaction.
- Do not modify already-applied Flyway migrations `V1` or `V2`; add `V3__xxl_job_scheduler.sql`.
- Do not enable XXL sharding broadcast for the globally ordered SAP cursor.
- Do not use GLUE scripts or expose the Admin password/access token to the browser.
- Seed XXL task definitions in stopped state (`trigger_status=0`). Enable them in Admin only after the executor is online; SQL must not invent `trigger_next_time`.
- Store only `SHA-256(jobLogFileName)` as `xxl_log_key`, never the executor's absolute log path.
- An XXL handler must wait for the business run to reach a terminal state before returning success or failure.
- Exact duplicate XXL requests reuse the original business run. A different XXL request that collides on a busy resource records `JOB_BUSY` and fails back to XXL-JOB; it must not alias to the active run.
- Every planned verification command is gated by fresh user approval under the workspace `AGENTS.md` instruction.
- This workspace currently has no confirmed Git workflow. Treat the commit lines below as suggested checkpoints; do not initialize a repository or commit unless the user asks.

---

### Task 1: Add the XXL-JOB infrastructure boundary

**Files:**
- Modify: `deploy/docker-compose.yml`
- Create: `deploy/xxl-job/init/01-tables_xxl_job.sql`
- Create: `deploy/xxl-job/init/02-procurement_jobs.sql`
- Modify: `.env.example`
- Modify: `.env`
- Modify: `README.md`

**Interfaces:**
- Provides MySQL service `xxl-job-mysql` on host port `${XXL_JOB_MYSQL_PORT:-3307}`.
- Provides Admin service `xxl-job-admin` at `http://localhost:${XXL_JOB_ADMIN_PORT:-9090}/xxl-job-admin`.
- Accepts executor callbacks registered as `${XXL_JOB_EXECUTOR_ADDRESS:-http://host.docker.internal:9999}`.
- Does not share PostgreSQL credentials or tables with XXL-JOB.

- [ ] Copy the unmodified XXL-JOB 2.4.2 official schema into `01-tables_xxl_job.sql`, retaining its upstream version/source header.
- [ ] Add an idempotent seed script for executor group `procurement-integration-executor` and the two BEAN handlers. Resolve `job_group` with a subquery by `app_name`; do not assume an ID allocated by the official sample rows.
- [ ] Seed `sapCostCenterSyncJobHandler` with Cron `0 */10 * * * ?`, route `FAILOVER`, block `SERIAL_EXECUTION`, misfire `DO_NOTHING`, timeout `1800`, retry count `2`, and `trigger_status=0`.
- [ ] Seed `sapExchangeRateSyncJobHandler` with Cron `0 10 2 * * ?`, route `FAILOVER`, block `SERIAL_EXECUTION`, misfire `FIRE_ONCE_NOW`, timeout `900`, retry count `2`, and `trigger_status=0`.
- [ ] Add the MySQL service with a dedicated volume, UTF-8 settings, `Asia/Shanghai`, health check, and both init scripts mounted read-only.
- [ ] Add `xuxueli/xxl-job-admin:2.4.2`, make it depend on healthy MySQL, set the datasource URL/user/password/access token/timezone through environment variables, and expose port 9090.
- [ ] Add example variables for MySQL, Admin, access token, executor AppName, executor port/address, and log path. Use development-only values in `.env`; keep `.env.example` safe to commit.
- [ ] Document that MySQL init scripts run only for a fresh data volume, that the seed jobs initially remain stopped, and that the browser opens `/xxl-job-admin` after Admin becomes healthy.
- [ ] With separate user approval, validate Compose syntax and container health; do not start Docker during implementation by default.

**Suggested commit:** `feat(infra): add xxl-job admin and mysql`

---

### Task 2: Add the executor dependency and conditional configuration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/integration-service/pom.xml`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/config/XxlJobProperties.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/config/XxlJobConfiguration.java`
- Modify: `backend/integration-service/src/main/resources/application.yml`
- Modify: `backend/integration-service/src/main/resources/application-dev.yml`
- Modify: `backend/aggregation-service/src/main/resources/application.yml`
- Modify: `backend/aggregation-service/src/main/resources/application-dev.yml`
- Create: `backend/integration-service/src/test/java/com/example/procurement/integration/config/XxlJobConfigurationTest.java`

**Interfaces:**
- Binds `xxl.job.*` into one validated configuration object.
- Creates one `XxlJobSpringExecutor` only when `xxl.job.enabled=true`.
- Uses AppName `procurement-integration-executor`, executor port `9999`, and matching Admin/executor access tokens.
- Keeps production/default configuration disabled until explicitly enabled by environment.

- [ ] Add property `xxl-job.version=2.4.2` and manage `com.xuxueli:xxl-job-core` centrally in `backend/pom.xml`.
- [ ] Add `xxl-job-core` and test-scope `spring-boot-starter-test` to `integration-service`.
- [ ] Define `XxlJobProperties` with fields `enabled`, `adminAddresses`, `accessToken`, and nested executor fields `appName`, `address`, `ip`, `port`, `logPath`, `retentionDays`.
- [ ] Validate nonblank AppName and port range only when XXL is enabled; allow blank address/IP so XXL can infer values outside the Windows-host development case.
- [ ] Define `XxlJobConfiguration` with `@EnableConfigurationProperties` and `@ConditionalOnProperty(name="xxl.job.enabled", havingValue="true")`.
- [ ] Build `XxlJobSpringExecutor` from all bound values. Keep the bean destroy lifecycle managed by Spring.
- [ ] Add safe defaults to `integration-service/application.yml`: disabled, localhost Admin URL, port 9999, relative log path, retention 30.
- [ ] Enable XXL in both development launch modes, but set the registered address from `XXL_JOB_EXECUTOR_ADDRESS`; do not hardcode a production hostname.
- [ ] Ensure the aggregation module sees the same configuration through its dependency/component scan and does not declare a second executor bean.
- [ ] Write context-runner tests for disabled/no-bean, enabled/one-bean, and property binding. Do not execute them without approval.

**Suggested commit:** `feat(integration): configure conditional xxl executor`

---

### Task 3: Extend the SRM scheduler schema without breaking history

**Files:**
- Create: `backend/integration-service/src/main/resources/db/integration/migration/V3__xxl_job_scheduler.sql`
- Modify: `backend/integration-service/src/main/java/com/example/procurement/integration/job/JobContext.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/TriggerType.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/RetryOwner.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/XxlTriggerMetadata.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/XxlExecutionIdentity.java`
- Create: `backend/integration-service/src/test/java/com/example/procurement/integration/job/XxlExecutionIdentityTest.java`

**Interfaces:**
- `JobContext` becomes `JobContext(runId, type, mode, triggerType, resourceKey, ownerId, token, attempt)`.
- `XxlTriggerMetadata` is `(jobId, logKey, shardIndex, shardTotal, executorAddress)`.
- `XxlExecutionIdentity.from(jobCode, jobId, jobLogFileName, shardIndex)` returns the 64-character lowercase SHA-256 log key and deterministic request ID `xxl:{jobCode}:{jobId}:{logKey}:{shardIndex}`.

- [ ] Add nullable `handler_code` and `resource_key` to `job_definition`; backfill both current definitions, then set them `NOT NULL`.
- [ ] Map cost-center work to `SAP_COST_CENTER_WRITER` and exchange-rate work to `SAP_EXCHANGE_RATE_WRITER`.
- [ ] Add nullable `xxl_job_id`, `xxl_log_key`, `xxl_shard_index`, `xxl_shard_total`, and `executor_address` to `job_run`.
- [ ] Add partial unique index `uk_job_run_xxl_log_shard` on `(job_code, xxl_job_id, xxl_log_key, xxl_shard_index)` where `xxl_log_key is not null`.
- [ ] Insert dataset-level lease rows with `ON CONFLICT DO NOTHING`. Retain legacy job-code lease rows for history compatibility.
- [ ] Add indexes needed by exact XXL lookup and local pending-run claims.
- [ ] Add enums so retry ownership is explicit in Java instead of inferred from arbitrary strings.
- [ ] Implement identity hashing with UTF-8 and `MessageDigest`; reject a blank log filename because it would collapse unrelated executions onto one key.
- [ ] Add deterministic hash, differing shard, and blank-filename test cases. Do not execute them without approval.

**Suggested commit:** `feat(integration): persist xxl execution identity`

---

### Task 4: Make repository submission and leasing aware of entry type and resource

**Files:**
- Modify: `backend/integration-service/src/main/java/com/example/procurement/integration/job/JobRepository.java`
- Create: `backend/integration-service/src/test/java/com/example/procurement/integration/job/JobRepositoryContractTest.java`

**Interfaces:**
- Keep `UUID submit(...)` for manual/SRM calls.
- Add `XxlSubmission submitXxl(String jobCode, XxlTriggerMetadata metadata, String requestId)` where the result distinguishes `CREATED`, `DUPLICATE_TERMINAL`, and `BUSY`.
- Keep `JobContext claim(String owner)` for local, non-XXL pending work.
- Add `JobContext claim(UUID runId, String owner)` for the synchronous handler to claim exactly its own run.
- Make every lease operation use `context.resourceKey()`, never derive it from `context.type().jobCode`.
- Make `failure(context, problem, retryOwner)` choose terminal `FAILED` for XXL and existing backoff behavior for LOCAL.

- [ ] Refactor row-to-context mapping into one private method that reads `trigger_type` and joins `job_definition.resource_key`.
- [ ] Change local claim SQL to exclude `trigger_type='XXL'` so the background dispatcher cannot steal a synchronous handler run.
- [ ] Add an exact-run claim transaction: lock the target run, verify it is pending and enabled, acquire the dataset lease, increment the fence token, and mark the run running.
- [ ] Preserve the lock order `job_run -> job_lease -> business rows/checkpoint` everywhere.
- [ ] Add `submitXxl` transaction semantics: first return the row for the exact unique identity; otherwise create a dedicated XXL run; if another active run owns the same resource, finish this new run as `FAILED/JOB_BUSY` rather than returning the other run ID.
- [ ] Write `xxl_job_id`, `xxl_log_key`, shard fields, and executor address in the created run.
- [ ] Check `job_definition.enabled` before execution and finish disabled jobs as `FAILED/JOB_DISABLED`; `enabled` remains the SRM business kill switch, not the XXL Cron switch.
- [ ] Change `renew`, `release`, `fenced`, and stale-run recovery joins to use `resource_key`.
- [ ] In stale-run recovery, preserve retry ownership: stale XXL work becomes terminal failed for Admin-driven retry; local work may return to `RETRY_WAIT`.
- [ ] Preserve current request-key foreign keys and deletion ordering; do not add cascade deletes to hide integrity errors.
- [ ] Add repository contract cases for exact duplicate reuse, different trigger busy failure, exact claim, local claim exclusion, resource-level collision, and retry-owner status. These tests may use the project's PostgreSQL development database only after separate approval.

**Suggested commit:** `feat(integration): coordinate xxl runs with resource leases`

---

### Task 5: Extract one synchronous business execution coordinator

**Files:**
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/JobExecutionResult.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/JobExecutionCoordinator.java`
- Modify: `backend/integration-service/src/main/java/com/example/procurement/integration/job/SyncJobExecutor.java`
- Modify: `backend/integration-service/src/main/java/com/example/procurement/integration/masterdata/MasterDataSyncService.java`
- Create: `backend/integration-service/src/test/java/com/example/procurement/integration/job/JobExecutionCoordinatorTest.java`

**Interfaces:**
- `executeClaimed(JobContext context, RetryOwner retryOwner)` performs the complete synchronous lifecycle and returns a terminal `JobExecutionResult`.
- `executeFromXxl(jobCode, metadata, requestId)` submits/claims exactly one XXL run and waits for completion.
- `SyncJobExecutor` continues to dispatch only manual/local runs through its fixed two-thread bounded pool.

- [ ] Move the current execute/success/failure/release lifecycle from `SyncJobExecutor` into `JobExecutionCoordinator` without copying `MasterDataSyncService` logic.
- [ ] Always release a lease in `finally`, guarded by matching owner and fence token.
- [ ] Convert `IntegrationProblem` into a result containing run ID, success flag, error code, sanitized summary, and retryability.
- [ ] For `RetryOwner.XXL`, persist `FAILED` and return failure immediately; never set `RETRY_WAIT`.
- [ ] For `RetryOwner.LOCAL`, retain the existing exponential delay, cap, jitter, and max-attempt behavior.
- [ ] Check cancellation/interruption and lease validity at safe page boundaries. Do not interrupt an in-flight database commit.
- [ ] Keep heartbeat and recovery maintenance schedules, but make their active-context handling work for both local workers and synchronous XXL threads.
- [ ] Add coordinator tests proving success, business failure, unexpected failure sanitization, lease release, cancellation, and mutually exclusive retry ownership. Do not execute them without approval.

**Suggested commit:** `refactor(integration): share job execution coordinator`

---

### Task 6: Register the two XXL-JOB handlers

**Files:**
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/XxlRuntimeContext.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/XxlJobHelperRuntimeContext.java`
- Create: `backend/integration-service/src/main/java/com/example/procurement/integration/job/XxlMasterDataJobHandlers.java`
- Create: `backend/integration-service/src/test/java/com/example/procurement/integration/job/XxlMasterDataJobHandlersTest.java`

**Interfaces:**
- Handler names are exactly `sapCostCenterSyncJobHandler` and `sapExchangeRateSyncJobHandler`.
- Runtime context reads `XxlJobHelper.getJobId()`, `getJobLogFileName()`, `getShardIndex()`, and `getShardTotal()`.
- Success calls `XxlJobHelper.handleSuccess(message)`; failure calls `XxlJobHelper.handleFail(message)` only after SRM has persisted the business terminal state.

- [ ] Wrap XXL's static helper behind `XxlRuntimeContext` so handler behavior is unit-testable without a real Admin callback.
- [ ] Resolve a safe executor address from configuration/node identity; persist it as diagnostic metadata.
- [ ] For each handler, compute the log-key/request ID, call `JobExecutionCoordinator.executeFromXxl`, log the SRM run ID with `XxlJobHelper.log`, and report the terminal result.
- [ ] Treat an exact duplicate that already succeeded as XXL success and include the original run ID.
- [ ] Treat an exact duplicate that already failed as failure; do not silently create a third business run for the same XXL identity.
- [ ] Treat `JOB_BUSY`, `JOB_DISABLED`, authentication failures, data errors, and infrastructure failures as handler failures with sanitized messages.
- [ ] Add reflection tests for exact `@XxlJob` names and fake-runtime tests for success, duplicate success, busy, and failure. Do not execute them without approval.

**Suggested commit:** `feat(integration): add sap master data xxl handlers`

---

### Task 7: Retire duplicate business Cron while retaining internal maintenance

**Files:**
- Modify: `backend/integration-service/src/main/java/com/example/procurement/integration/job/SyncJobExecutor.java`
- Modify: `backend/integration-service/src/main/java/com/example/procurement/integration/web/IntegrationController.java`
- Modify: `backend/integration-service/src/main/resources/application.yml`
- Modify: `backend/integration-service/src/main/resources/application-dev.yml`
- Create: `backend/integration-service/src/test/java/com/example/procurement/integration/job/BusinessSchedulingOwnershipTest.java`

**Interfaces:**
- XXL-JOB is the only automatic business scheduler.
- Spring scheduling remains only for local dispatch, heartbeat, stale-run recovery, and stale-alert maintenance.
- `GET /api/integration/overview` exposes safe scheduler metadata but never secrets.
- `PATCH /api/integration/jobs/{jobCode}` toggles the SRM business execution switch, not the XXL task status.

- [ ] Remove startup catch-up submission, `triggerCron`, its in-memory `nextFire` map, and the weekly reconciliation `@Scheduled` method.
- [ ] Retain local dispatch (1 second), heartbeat (15 seconds), recovery (15 seconds), and stale alert detection (60 seconds).
- [ ] Remove `integration.sync.scheduler-enabled` from runtime decisions and configuration.
- [ ] Add overview fields `schedulerProvider="XXL_JOB"`, `xxlEnabled`, `xxlAdminUrl`, and `xxlExecutorAppName`.
- [ ] Expose only an allowlisted Admin HTTP(S) URL from configuration. Never return access token, MySQL credentials, Admin username, or password.
- [ ] Include `handlerCode` and `resourceKey` in job definitions and XXL diagnostic fields in run detail/list rows.
- [ ] Return a clear error when a disabled job is manually or externally executed, while leaving already-running work unchanged.
- [ ] Add a structural test that forbids business-Cron methods in `SyncJobExecutor` and checks the overview response has no secret fields. Do not execute it without approval.

**Suggested commit:** `refactor(integration): make xxl sole business scheduler`

---

### Task 8: Make XXL scheduling visible in the SRM frontend

**Files:**
- Modify: `web/src/types/integration.ts`
- Modify: `web/src/api/integration/index.ts`
- Modify: `web/src/views/integration/SyncCenter.vue`
- Modify: `web/src/views/integration/RunDetailDrawer.vue`
- Create: `web/src/utils/xxlJobDisplay.ts`
- Create: `web/src/utils/xxlJobDisplay.test.ts`

**Interfaces:**
- `Overview` gains `schedulerProvider`, `xxlEnabled`, `xxlAdminUrl`, and `xxlExecutorAppName`.
- `JobDefinition` gains `handlerCode` and `resourceKey`.
- `JobRun` gains optional `xxlJobId`, `xxlLogKey`, `xxlShardIndex`, `xxlShardTotal`, and `executorAddress`.
- Browser continues calling the same `/api/integration/**` endpoints.

- [ ] Update TypeScript types with nullable/optional XXL fields so historical V1/V2 runs still render.
- [ ] Add a compact “XXL-JOB 调度中心” status card showing enabled state, Admin URL, executor AppName, and an external-link button.
- [ ] Open the Admin URL in a new tab with `noopener,noreferrer`; do not iframe it and do not proxy credentials.
- [ ] Change the job-card switch label from “定时任务” to “业务执行开关” and update success messages so users do not mistake it for Admin Cron enablement.
- [ ] Show handler name, resource key, and configured Cron in each task card. State that schedule enable/disable and Cron editing occur in XXL-JOB Admin.
- [ ] In the run table/detail drawer, show trigger source `XXL`, XXL job ID, shortened log-key digest, executor address, and shard `index/total` only when present.
- [ ] Add a pure display helper for log-key shortening and safe Admin URL validation, with tests for null, invalid protocol, and valid HTTP(S). Do not execute tests or frontend build without approval.
- [ ] Preserve responsive behavior and existing cost-center, exchange-rate, reconciliation, alerts, and mock-SAP controls.

**Suggested commit:** `feat(web): visualize xxl scheduling and run metadata`

---

### Task 9: Add operation documentation and an approval-gated verification script

**Files:**
- Modify: `README.md`
- Modify: `docs/11-run-modes.md`
- Create: `resource/项目发开文档/简历五大功能技术实现方案/02-XXL-JOB实现与演示指南.md`
- Create: `scripts/verify-xxl-job-scheduling.ps1`

**Interfaces:**
- Documents one development start sequence: infrastructure -> aggregation 8090/executor 9999 -> gateway 8080 -> frontend 5173.
- Documents one production sequence: XXL Admin/MySQL -> integration-service cluster -> gateway/frontend.
- Verification script is read-only toward business tables except for explicitly triggered demo jobs and uses existing management credentials from caller-supplied parameters.

- [ ] Document ports 3307, 9090, 9999, 8090, 8080, and 5173 and explain why standalone `integration-service` must remain stopped in aggregation mode.
- [ ] Document first-login actions: change the default Admin password, confirm the executor registration, inspect the two seeded stopped tasks, then enable them.
- [ ] Document aggregation and microservice environment variables, including the Windows/Docker `host.docker.internal` registered address.
- [ ] Explain the two-layer status model: an XXL scheduling success is valid only after the linked SRM run reaches a success terminal state.
- [ ] Explain why XXL-triggered failures do not enter local `RETRY_WAIT`, while SRM manual failures still do.
- [ ] Add troubleshooting for Admin 502/unavailable, executor offline, port 9999 conflict, token mismatch, stale MySQL volume, and `JOB_BUSY`.
- [ ] Create a PowerShell verification script that accepts base URLs and admin key as parameters, checks health/overview fields, triggers only an explicitly requested handler, and prints linked run metadata. It must not start or stop existing user processes.
- [ ] Mark all run/build/test/database steps in the guide as requiring user confirmation in this workspace.

**Suggested commit:** `docs: add xxl-job runbook and verification workflow`

---

### Task 10: Approval-gated verification and evidence capture

**Files:**
- Modify only if verification uncovers a defect: files owned by Tasks 1-9.
- Create after successful verification: `resource/项目发开文档/简历五大功能技术实现方案/02-XXL-JOB验证记录.md`

**Interfaces:**
- Produces evidence for configuration, unit behavior, build compatibility, runtime registration, failover semantics, and frontend visibility.
- Does not alter unrelated services or existing business data.

- [ ] Stop and request the user's approval before running any command in this task.
- [ ] If approved, run the targeted Java unit tests for configuration, identity, repository, coordinator, handlers, and scheduling ownership.
- [ ] If approved, run the targeted frontend tests and production build.
- [ ] If approved, validate Docker Compose, start only XXL MySQL/Admin when needed, and confirm Admin health.
- [ ] If approved, start an isolated aggregation executor and gateway only if the user is not already running them; never kill user-owned processes.
- [ ] Confirm the executor registers once under `procurement-integration-executor` and both handlers are discoverable.
- [ ] Enable and manually trigger the cost-center handler in Admin; confirm one linked SRM run, terminal success, counters, checkpoint, XXL job ID/log key/executor address, and frontend display.
- [ ] Inject one mock SAP transient failure, confirm the XXL attempt fails without `RETRY_WAIT`, then confirm the Admin retry resumes from the last successful checkpoint.
- [ ] Trigger a concurrent SRM manual run and XXL run; confirm only one gets `SAP_COST_CENTER_WRITER` and the other records `JOB_BUSY` without concurrent writes.
- [ ] If a second isolated executor is explicitly approved, confirm failover and stale-owner fencing; otherwise record that multi-node failover was not executed.
- [ ] Capture exact commands, timestamps, versions, pass/fail results, known limitations, and any unexecuted cases in the verification record. Do not claim unexecuted scenarios passed.

**Suggested commit:** `test: verify xxl scheduling and compensation`

## Done Criteria

- XXL-JOB Admin owns both business schedules and discovers both handlers.
- Aggregation development mode requires no additional manually started backend service.
- The standalone integration-service can register the same AppName in production mode without frontend changes.
- Manual and XXL execution share business code, resource leases, fencing, checkpoints, and run visibility.
- XXL failures are terminal in SRM and retried only by XXL; local/manual failures retain SRM persistent retry.
- Duplicate callbacks are idempotent and different busy triggers are explicit failures, not silent aliases.
- The frontend displays scheduling state and per-run XXL metadata without exposing secrets.
- Documentation distinguishes what was implemented from what was actually verified.

## Implementation Order and Checkpoints

Implement Tasks 1-3 first and review configuration/schema compatibility. Then implement Tasks 4-7 as one backend behavior slice. Implement Task 8 after the overview/run response shape is stable. Finish documentation in Task 9. Task 10 remains blocked on explicit user approval for testing and runtime verification.
