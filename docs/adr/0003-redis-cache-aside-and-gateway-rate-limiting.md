# ADR 0003: Redis for cache-aside product reads and gateway rate limiting

**Status:** Accepted (Phase 3)

## Context
Product detail reads are the most frequent request in a shop and change rarely. Every
read currently hits PostgreSQL. Separately, the public API has no protection against
password guessing on login, mass registration, or a single client flooding the system.
We run (or will run) more than one instance of each component, so any per-instance,
in-memory state would be inconsistent between instances.

## Decision
1. Use **Redis** as shared, non-authoritative state. PostgreSQL stays the source of truth.
2. **Product cache:** cache-aside via Spring's `@Cacheable` on `ProductService.getById`.
   - Values are JSON typed to `ProductResponse`, under versioned keys
     `shopflow:v1:products::{id}`, with a TTL (default 10 min, `PRODUCT_CACHE_TTL`).
   - Writes **evict** rather than update. Eviction is triggered by a `ProductsChangedEvent`
     and runs **after the database commit** (`@TransactionalEventListener(AFTER_COMMIT)`).
     Product update/delete and stock reservation/release all publish it.
   - The cache advice runs outside the transaction advice, so cache hits use no DB connection.
   - Search results and internal stock endpoints are **not** cached.
   - Cache errors **fail open** (treated as misses); Redis timeouts are 500 ms; the Redis
     health indicator is disabled because the service works without Redis.
3. **Rate limiting** at the API gateway with Spring Cloud Gateway's `RequestRateLimiter`
   and `RedisRateLimiter` (token bucket, atomic Lua script in Redis).
   - Login/register: keyed by client IP; bursts of 10, then 10/min.
   - All other routes: keyed by the JWT subject **after signature verification**, else by IP;
     bursts of 20, then 10/s.
   - Client IP comes from the TCP connection; `X-Forwarded-For` is not trusted.
   - Rejections return `429` with the standard ShopFlow JSON error body and
     `X-RateLimit-*` headers. The limiter fails open if Redis is unavailable.

## Consequences
- Positive: fewer database reads for hot products; protection for login and fair usage
  for everyone; limits hold across multiple gateway instances; Redis outages degrade
  latency instead of availability.
- Negative: cache-aside is eventually consistent. A rare race can leave a stale entry,
  bounded by the TTL. One more piece of infrastructure to run and monitor. The gateway now
  holds the JWT secret (it could mint tokens), the same trade-off every service already has
  with HS256. Fail-open means no rate limiting during a Redis outage.
- Follow-ups: cache hit/miss metrics and Redis alerts (Phase 10); TTL jitter and stampede
  protection if load tests show the need; trusted-proxy IP resolution once behind a load
  balancer (Phase 8/11); consider RS256 so only user-service can sign tokens.

## Alternatives considered
- **Local in-process cache (Caffeine):** fastest, but each instance has its own copy, so
  evictions would have to be broadcast to every instance. A good later addition as an L1 cache.
- **Hibernate second-level cache:** caches entities, not API responses, and is harder to
  reason about across services.
- **HTTP caching (ETag/Cache-Control) or a CDN:** complementary for public catalog data;
  doesn't help the service's own database load for authenticated clients.
- **In-memory rate limiter (e.g. Bucket4j with a local cache):** no extra infrastructure,
  but limits multiply with the number of gateway instances.
- **Rate limiting in each service:** duplicated logic, and requests already consumed
  service resources before being rejected.
