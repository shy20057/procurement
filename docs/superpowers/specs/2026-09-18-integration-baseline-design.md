# Procurement Integration Baseline Design

## Goal

Restore a verifiable browser-to-database development path and establish the minimum automated regression baseline needed for continued enterprise-oriented development.

## Scope

- Keep the current default `dev` topology: Vue/Vite -> API Gateway -> Nacos-discovered supplier service -> PostgreSQL.
- Make frontend API URLs resolve exactly once under `/api`.
- Make `lb://srm-service` executable by adding Spring Cloud LoadBalancer to the gateway.
- Remove duplicate MyBatis mapper scanning while preserving all mapper discovery.
- Add focused frontend and backend regression tests for these failures.
- Verify frontend build, backend reactor build, service health, gateway forwarding, and Vite proxy forwarding.

## Non-goals

- Production authentication and authorization.
- Rate limiting, circuit breaking, tracing, deployment manifests, or shared internal starters.
- Redesigning domain APIs or database schemas.
- Enabling the optional aggregation profile.

## Architecture

The browser calls `/api/srm/**` on Vite. Vite proxies `/api` to the gateway on port 8080 without rewriting the path. The gateway matches `/api/srm/**`, resolves `srm-service` through Nacos, and Spring Cloud LoadBalancer selects a healthy instance. The supplier service handles the unchanged path and accesses PostgreSQL.

## Error handling

The existing Axios interceptor continues to display backend error codes and messages. Gateway routing failures remain HTTP 503, but the regression suite proves a healthy registered service is routable.

## Testing

- A Vitest regression test proves Axios composes `/api/srm/**` without duplicating `/api`.
- A gateway JUnit test proves the LoadBalancer runtime is present.
- A supplier JUnit test proves mapper scanning is declared in only one configuration location.
- Full frontend and Maven builds must pass.
- Runtime smoke tests must return HTTP 200 through both gateway and Vite proxy.

