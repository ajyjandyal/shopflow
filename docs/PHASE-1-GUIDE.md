# Phase 1 Guide: Modular Monolith REST API

This guide explains what Phase 1 contains, why each piece exists, how to run and test
it, how to fix common problems, and what interviewers will ask about it.

---

## 1. What we built and why

A single Spring Boot application that lets people register, browse products, manage a
cart, and place and track orders, with sellers managing products and admins managing orders.

**Why a monolith first?** Microservices solve organisational and scaling problems, but
they add network failures, distributed data and deployment complexity. If the business
rules are wrong, splitting them across services just spreads the bugs. So we get the
domain correct in one process first, structured so it splits cleanly later (see ADR 0001).
Saying this in an interview signals maturity: "I'd start with a modular monolith and
extract services when there's a reason to."

---

## 2. Concepts you need

**REST API design.** Resources are nouns (`/products`, `/orders`), HTTP methods are verbs
(GET reads, POST creates, PUT replaces, PATCH partially updates, DELETE removes). Status
codes carry meaning: 200 OK, 201 Created (with a `Location` header), 204 No Content,
400 bad input, 401 not authenticated, 403 authenticated but not allowed, 404 not found,
409 conflict with current state, 500 server bug. We version the API in the URL (`/api/v1`)
so a future v2 doesn't break existing clients.

**Layered architecture.** Controller (HTTP in/out, validation) → Service (business rules,
transactions) → Repository (database access) → Entity (data + invariants). DTOs (request/
response records) are separate from entities so we never accidentally expose fields such as
`passwordHash`, and so the API shape can change independently from the database.

**JPA / Hibernate.** Maps Java classes to tables. Key ideas used here: the *persistence
context* (Hibernate tracks loaded entities and writes changes on commit, called dirty
checking, which is why `update()` never calls `save()`); *lazy loading*; *entity graphs*
(load a parent and its children in one query); *optimistic locking* with `@Version`.

**Flyway.** Versioned SQL migrations (`V1__init_schema.sql`). Hibernate is set to
`ddl-auto: validate`: it checks the schema matches the entities but never changes it.
Real teams never let Hibernate auto-create production tables.

**Transactions (ACID).** `@Transactional` makes a method all-or-nothing. If checkout fails
halfway (say, the third item is out of stock), the stock already decremented for items one
and two is rolled back automatically.

**Authentication vs authorization.** Authentication = who are you (login → JWT).
Authorization = what may you do (roles + ownership rules).

**JWT.** A signed token: `header.payload.signature`, each Base64URL-encoded. The payload is
*readable by anyone* (it is encoded, not encrypted), so it never contains secrets. The
signature (HMAC-SHA256 with our server secret) proves the server issued it and nobody
modified it. The server stores no session: every request carries its own proof, which is
what makes the API *stateless* and easy to scale horizontally.

**BCrypt.** A deliberately slow, salted password hash. Salting means two users with the same
password get different hashes; slowness (cost factor 12) makes brute-forcing stolen hashes
expensive. We never store or log plain passwords.

**RBAC.** Roles USER, SELLER, ADMIN. Enforced in two layers: URL rules in `SecurityConfig`,
and `@PreAuthorize` on methods. Roles are not enough on their own: a SELLER may only edit
*their own* products, which is an ownership check in the service.

**Concurrency.** Two users buying the last unit at the same moment is a race condition.
We use a pessimistic lock (`SELECT ... FOR UPDATE`) during checkout so the second
transaction waits for the first, then sees the updated stock.

**Pagination.** Never return unbounded lists. `?page=0&size=20&sort=price,asc`. Page size is
capped at 100 and sort fields are whitelisted.

---

## 3. Code walkthrough (the parts that matter most)

### Request flow
`HTTP request` → `JwtAuthenticationFilter` (reads `Authorization: Bearer ...`, verifies the
token, puts an `AuthenticatedUser` into the `SecurityContext`) → Spring Security
authorization rules → controller (`@Valid` triggers bean validation) → service
(`@Transactional`) → repository → PostgreSQL. Any exception travels back up to
`GlobalExceptionHandler`, which converts it into one consistent JSON error format.

### `security/JwtAuthenticationFilter`
If a token is missing or invalid, the filter does *not* reject the request. It just leaves
it anonymous. Public endpoints still work; protected ones return 401 via
`RestAuthenticationEntryPoint`. It is deliberately not a `@Component`, because Spring Boot
registers every Filter bean with the servlet container automatically, which would run it
outside the security chain too.

### `security/JwtService`
Signs tokens with HS256. The key is decoded from the Base64 `JWT_SECRET`;
`Keys.hmacShaKeyFor` refuses keys under 256 bits, so a weak secret stops the app at startup.
Parsing verifies the signature, expiry and issuer in one call.

### `auth/AuthService`
Login returns the same message for "unknown email" and "wrong password", and checks a dummy
hash when the email doesn't exist so both cases take similar time. Both measures prevent
attackers from discovering which emails are registered (user enumeration).

### `product/Product` (rich domain model)
Stock changes only through `decreaseStock` / `increaseStock`, which enforce "stock never
goes negative" in one place. The database also has `CHECK (stock_quantity >= 0)`, a second
line of defence. Delete is a *soft delete* (`active = false`) because carts and order
history still reference products.

### `product/ProductSpecifications`
Dynamic filtering: only the filters the client sends are added to the query. All values
are bound as parameters (no string-concatenated SQL, so no SQL injection), and LIKE
wildcards in the search text are escaped so searching `50%` matches literally.

### `cart/CartService.toResponse` (N+1 avoidance)
The naive approach loops over cart items and calls `findById` for each product: 1 + N
queries. Instead we collect all product ids, run *one* `WHERE id IN (...)` query, put the
results in a `HashMap`, and look each one up in O(1). This is a DSA idea (hash-based
lookup) directly improving database performance.

### `order/OrderService.placeOrder` (the most important method)
1. Load the cart (one query with its items).
2. `findAllByIdForUpdate`: lock all product rows *sorted by id*. Consistent lock ordering
   prevents deadlocks (two transactions can't each hold a lock the other wants).
3. For each item: check the product is active, decrement stock, snapshot name and price
   into an `OrderItem`.
4. Move the order PENDING → CONFIRMED, save it, clear the cart.
Any exception rolls back everything.

### `order/OrderStatus` (state machine)
Allowed transitions live in an `EnumMap` of `EnumSet`s. `Order.changeStatus` rejects
illegal moves (e.g. DELIVERED → CANCELLED) with 409. Cancelling a CONFIRMED order returns
its stock.

### `order/OrderService.loadAccessibleOrder`
A user requesting someone else's order gets **404, not 403**. A 403 would confirm that the
order id exists. This defends against IDOR (Insecure Direct Object Reference) probing.

### `common/exception/GlobalExceptionHandler`
Maps every exception type to an HTTP status. Note the explicit `AccessDeniedException`
handler: without it, `@PreAuthorize` failures would hit the catch-all and become 500s.
Unexpected errors are logged with stack traces server-side, but clients only see a generic
message.

---

## 4. Run it

```bash
# from the repository root
cp .env.example .env
openssl rand -base64 32          # put result in JWT_SECRET
# edit .env: set DB_PASSWORD and ADMIN_PASSWORD (12+ chars)

docker compose up -d
docker compose ps                # STATUS should show (healthy)

cd backend
mvn spring-boot:run
```

Success looks like log lines showing Flyway applying `V1__init_schema`, then
`Admin bootstrap: created admin account admin@shopflow.local`, then
`Tomcat started on port 8080`.

No `openssl`? On Windows PowerShell:
`[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))`

---

## 5. Test it

### Automated unit tests
```bash
cd backend
mvn test
```
These need no database. They cover JWT issuing/verification, the order state machine,
stock invariants, LIKE escaping, and checkout logic (with Mockito mocks). Database
integration tests with Testcontainers come in Phase 6.

### Manual end-to-end test (bash / Git Bash)
Swagger UI (http://localhost:8080/swagger-ui.html) does all of this with clicks: call
login, copy the `accessToken`, press **Authorize**, paste it. Below is the same flow with curl.

```bash
API=http://localhost:8080/api/v1

# 1. Register a seller and a buyer
curl -s -X POST $API/auth/register -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"Seller1234","fullName":"Sam Seller","accountType":"SELLER"}'
curl -s -X POST $API/auth/register -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"Buyer1234","fullName":"Bea Buyer"}'

# 2. Log in and copy the accessToken values into variables
SELLER=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"Seller1234"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
BUYER=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"Buyer1234"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 3. Seller creates a product (note the 201 and Location header)
curl -i -X POST $API/products -H "Authorization: Bearer $SELLER" -H "Content-Type: application/json" \
  -d '{"name":"Mechanical Keyboard","description":"Tactile switches","category":"Electronics","price":49.99,"stockQuantity":3}'

# 4. Anyone can browse; try filters, paging and sorting
curl -s "$API/products?q=keyboard&category=electronics&page=0&size=10&sort=price,asc"

# 5. Buyer adds 2 to cart, views cart, checks out
curl -s -X POST $API/cart/items -H "Authorization: Bearer $BUYER" -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
curl -s $API/cart -H "Authorization: Bearer $BUYER"
curl -i -X POST $API/orders -H "Authorization: Bearer $BUYER"

# 6. Stock is now 1; my orders list
curl -s $API/products/1
curl -s $API/orders -H "Authorization: Bearer $BUYER"
```

### Negative tests (each should fail with the stated status)
| Try this | Expected |
|---|---|
| Register with password `short` | 400 with `fieldErrors.password` |
| Register the same email twice | 409 |
| Login with wrong password | 401 "Invalid email or password" |
| `POST /products` as the buyer | 403 |
| `POST /products` with no token | 401 |
| `GET /cart` with a modified token (change one character) | 401 |
| Add 5 of a product with stock 1 | 409 |
| `POST /orders` with an empty cart | 400 |
| Buyer requests another user's order id | 404 |
| `?sort=passwordHash` on products | 400 |
| `?size=5000` | Silently capped at 100 |
| Admin moves a DELIVERED order to CANCELLED | 409 |
| `GET /api/v1/admin/orders` as buyer | 403 |

### Inspect the database
```bash
docker exec -it shopflow-postgres psql -U shopflow -d shopflow
\dt                                       -- list tables
SELECT * FROM flyway_schema_history;      -- applied migrations
SELECT id, email, role FROM users;        -- note: password_hash starts with $2a$12$
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 2 ORDER BY created_at DESC LIMIT 20;
\q
```
With only a few rows, PostgreSQL may choose a sequential scan even though an index exists;
that is correct behaviour for tiny tables. Index benefits show up at scale, which we will
measure in Phase 10.

---

## 6. Common errors and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `JWT_SECRET environment variable must be set` at startup | `.env` missing or JWT_SECRET empty | Create `.env` from `.env.example`, set JWT_SECRET |
| `WeakKeyException` at startup | Secret decodes to under 32 bytes, or isn't Base64 | Use `openssl rand -base64 32` output exactly |
| `Connection to localhost:5432 refused` | PostgreSQL container not running | `docker compose up -d`, wait for healthy |
| `port is already allocated` for 5432 | A local PostgreSQL already uses 5432 | Set `DB_PORT=5433` and `DB_URL=...localhost:5433/...` in `.env` |
| `password authentication failed for user` | Volume was created with an old password | `docker compose down -v` (deletes data), then `up -d` |
| `Schema-validation: missing table` / `wrong column type` | Entity and migration disagree | Fix the mismatch; for a changed schema add a new `V2__...sql` |
| `Validate failed: Migrations have failed validation` / checksum mismatch | You edited an applied migration | Revert the edit and add a new migration (locally only: `docker compose down -v` to reset) |
| `Unsupported Database: PostgreSQL 16` (Flyway) | `flyway-database-postgresql` dependency missing | Keep both Flyway dependencies from the pom |
| `release version 21 not supported` | Maven is using an older JDK | Set `JAVA_HOME` to JDK 21; check `mvn -version` |
| 401 on every request in Swagger | Token not set, or pasted with "Bearer " prefix | Click Authorize, paste only the token |
| 403 instead of 401 | You are logged in but lack the role | Expected; use the right account |
| 500 with log `LazyInitializationException` | Lazy relation accessed outside a transaction | Map to DTOs inside `@Transactional` service methods (open-in-view is off on purpose) |
| CORS error in browser console | Frontend origin not allowed | Add the origin to `CORS_ALLOWED_ORIGINS` |
| `.env` values ignored | Running from a different working directory | Run from `backend/` or repo root, or export variables in the shell |

---

## 7. Interview questions on Phase 1

**Architecture and design**
1. Why did you start with a monolith if the goal is microservices? *(Validate the domain first; module boundaries by feature; references by id; split when needed.)*
2. Why package by feature instead of by layer? *(High cohesion; each module is a future service; changes stay local.)*
3. Why separate DTOs from entities? *(Avoid leaking fields like passwordHash, decouple API from schema, avoid lazy-loading surprises during serialization.)*
4. Why is `open-in-view` disabled? *(Prevents hidden queries in the view layer and long-held DB connections; forces explicit fetching.)*
5. What does `ddl-auto: validate` do and why not `update`? *(Schema changes must be reviewed, versioned and repeatable; Flyway owns them.)*

**Security**
6. Walk me through what happens when a request with a JWT arrives.
7. Is a JWT encrypted? What can an attacker do if they steal one? *(Encoded, not encrypted; they can impersonate the user until expiry; mitigations: short expiry, HTTPS, refresh tokens, revocation list.)*
8. A user's role changes from SELLER to USER. When does it take effect, and how would you make it immediate? *(At token expiry; options: shorter expiry, token versioning in the DB, denylist.)*
9. Why BCrypt instead of SHA-256? Why is the max password length 72?
10. Why disable CSRF? When would that be wrong? *(No cookies are used; with cookie-based auth CSRF protection is required.)*
11. Why return 404 instead of 403 for someone else's order? *(Don't confirm the resource exists: IDOR hardening.)*
12. How does your code prevent SQL injection? *(Parameter binding via JPA/Criteria; no string-built SQL; LIKE wildcards escaped.)*
13. Why can't users register as ADMIN, and how is the first admin created?

**Data and concurrency**
14. Two users buy the last item at the same time. What happens in your system? *(Row lock with SELECT FOR UPDATE; second transaction waits, then sees stock 0 and gets 409; whole transaction rolls back.)*
15. Pessimistic vs optimistic locking: where do you use each and why? *(Pessimistic for hot, contended stock rows during checkout; @Version optimistic for rarely-conflicting edits.)*
16. How do you avoid deadlocks when locking several products? *(Always lock rows in the same order, by id.)*
17. Why copy product name and price into order items? *(Historical correctness; prices change.)*
18. What is the N+1 query problem and where did you avoid it?
19. Why can't you paginate a JOIN FETCH of a collection efficiently? *(Row multiplication; Hibernate paginates in memory.)*
20. Why soft delete products?
21. Which indexes did you create and why? Why doesn't `LIKE '%keyboard%'` use a B-tree index? *(Leading wildcard; options: pg_trgm GIN index or full-text search.)*
22. Why `BigDecimal` for money and `NUMERIC(12,2)` in the database? *(Floating point can't represent 0.1 exactly.)*

**API design**
23. Why 201 with a Location header for creation? Why 409 rather than 400 for insufficient stock?
24. Why whitelist sort fields and cap page size?
25. Offset pagination gets slow on page 10,000. Why, and what is the alternative? *(DB still scans skipped rows; keyset/cursor pagination.)*
26. How would checkout change once Product and Order are separate services? *(No shared transaction; leads to events, sagas and eventual consistency in Phase 4.)*

**OOP / DSA**
27. Where is encapsulation enforced in your domain model? *(Private state, invariants in `decreaseStock`, unmodifiable item lists, protected JPA constructors.)*
28. How is the order lifecycle modelled? *(Finite state machine using EnumMap/EnumSet: O(1) transition checks.)*
29. Which data structures did you use to make cart rendering efficient? *(HashSet of ids + HashMap lookup.)*

---

## 8. Phase 1 "done" checklist
- [ ] `mvn test` passes
- [ ] App starts; Flyway applies V1; admin account is created
- [ ] Swagger UI loads and Authorize works
- [ ] Full curl flow works: register → create product → cart → order → stock decreases
- [ ] Every negative test in the table returns the expected status
- [ ] Code committed to GitHub (with `.env` NOT committed)

Suggested commits:
```bash
git init
git add .
git commit -m "feat: phase 1 modular monolith with JWT auth, RBAC, products, cart, orders"
```
