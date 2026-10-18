# Procurement Integration Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the Vue-to-Gateway-to-supplier-service integration path and protect it with automated tests.

**Architecture:** Keep the existing development topology and API paths. Add the missing gateway load-balancer runtime, ensure Axios does not add a second `/api` prefix, and make mapper discovery single-owned.

**Tech Stack:** Vue 3, TypeScript, Vite, Vitest, Axios, Java 17, Spring Boot 3, Spring Cloud Gateway, Nacos, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-18-integration-baseline-design.md`

## Global Constraints

- Preserve the public `/api/srm/**` contract.
- Preserve the default `dev` direct-to-microservice topology.
- Use Java 17 for every Maven command.
- Do not change database schemas or domain behavior.
- Every behavior change starts with a failing regression test.

---

### Task 1: Frontend API URL composition

**Files:**
- Modify: `web/package.json`
- Modify: `web/src/utils/request.ts`
- Create: `web/src/utils/request.test.ts`
- Modify: `web/package-lock.json`

**Interfaces:**
- Consumes: SRM API methods that already pass absolute same-origin paths such as `/api/srm/invitations`.
- Produces: Axios requests whose resolved path is exactly `/api/srm/**`.

- [ ] Add Vitest as a development test dependency and a `test` script.
- [ ] Write a test asserting `request.getUri({ url: '/api/srm/invitations' })` equals `/api/srm/invitations`.
- [ ] Run the test and confirm it fails with `/api/api/srm/invitations`.
- [ ] Change the Axios base URL to an environment-configurable empty default.
- [ ] Run the test and frontend build and confirm both pass.

### Task 2: Gateway service discovery forwarding

**Files:**
- Modify: `backend/api-gateway/pom.xml`
- Create: `backend/api-gateway/src/test/java/com/example/procurement/gateway/GatewayRuntimeDependenciesTest.java`

**Interfaces:**
- Consumes: `lb://srm-service` from `application-dev.yaml` and the Nacos service registry.
- Produces: a gateway runtime containing Spring Cloud LoadBalancer.

- [ ] Add the Spring Boot test starter in test scope.
- [ ] Write a JUnit test that asserts the LoadBalancer runtime class is present.
- [ ] Run the gateway test and confirm the assertion fails.
- [ ] Add `spring-cloud-starter-loadbalancer` to gateway runtime dependencies.
- [ ] Run the gateway test and confirm it passes.

### Task 3: Single-owner mapper scanning

**Files:**
- Modify: `backend/supplier-service/pom.xml`
- Modify: `backend/supplier-service/src/main/java/com/example/procurement/supplier/config/MybatisPlusConfig.java`
- Create: `backend/supplier-service/src/test/java/com/example/procurement/supplier/config/MybatisMapperScanConfigurationTest.java`

**Interfaces:**
- Consumes: mapper interfaces below `com.example.procurement.supplier.dao.mapper`.
- Produces: one mapper scan rooted at the application class, without duplicate bean registration.

- [ ] Add the Spring Boot test starter in test scope.
- [ ] Write a reflection test asserting `MybatisPlusConfig` does not declare `@MapperScan`.
- [ ] Run the supplier test and confirm it fails.
- [ ] Remove the redundant annotation and import from `MybatisPlusConfig`.
- [ ] Run the supplier tests and confirm they pass.

### Task 4: Full verification

**Files:**
- No production file changes.

**Interfaces:**
- Consumes: deliverables from Tasks 1-3.
- Produces: evidence for build and runtime integration health.

- [ ] Run `npm.cmd test -- --run` in `web`.
- [ ] Run `npm.cmd run build` in `web`.
- [ ] Run Maven `clean verify` for the backend reactor with Java 17.
- [ ] Launch the rebuilt gateway on an isolated verification port.
- [ ] Assert the supplier direct endpoint and gateway endpoint both return HTTP 200.
- [ ] Launch Vite and assert `/api/srm/invitations` returns HTTP 200 through its proxy.
- [ ] Stop only the temporary verification processes.

