#!/usr/bin/env bash
# End-to-end check of the whole system THROUGH THE GATEWAY (port 8080).
# Uses fresh random emails each run, so it can be repeated.
# Phase 3 adds checks for the Redis product cache and the gateway rate limiter.
# Note: login/register are limited to bursts of 10 per IP (then 10/min). Each run registers
# 2 users, so running this more than ~5 times within a minute gives 429 on register. That
# is the rate limiter working; wait a minute and run it again.
set -uo pipefail
BASE=${BASE_URL:-http://localhost:8080}
RUN=$(date +%s)
FAILED=0
STATUS=""; BODY=""
TMP=$(mktemp -d "${TMPDIR:-/tmp}/shopflow-smoke.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

if ! command -v redis-cli >/dev/null 2>&1; then
  echo "ERROR: redis-cli not found (brew install redis)"; exit 1
fi

request() { # method path [token] [json-body]
  local method=$1 path=$2 token=${3:-} body=${4:-} out
  local args=(-s -X "$method" "$BASE$path" -H "Content-Type: application/json" -D "$TMP/headers" -w $'\n%{http_code}')
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$body" ]; then args+=(-d "$body"); fi
  out=$(curl "${args[@]}")
  STATUS=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

expect() { # expected-status description
  if [ "$STATUS" = "$1" ]; then
    echo "  PASS  $2"
  else
    echo "  FAIL  $2 (expected $1, got $STATUS)"
    echo "        response: $BODY"
    FAILED=1
  fi
}

check() { # condition-result(0/1) description [hint]
  if [ "$1" = "0" ]; then echo "  PASS  $2"; else echo "  FAIL  $2"; [ -n "${3:-}" ] && echo "        $3"; FAILED=1; fi
}

field() { # json-field-name -> value from $BODY (simple flat fields only)
  echo "$BODY" | sed -E 's/.*"'"$1"'":"?([^",}]*)"?.*/\1/'
}

header() { # header-name -> value from the last response
  grep -i "^$1:" "$TMP/headers" | head -n 1 | cut -d: -f2- | tr -d ' \r'
}

echo "ShopFlow smoke test against $BASE"

request POST /api/v1/auth/register "" "{\"email\":\"seller$RUN@test.com\",\"password\":\"Seller1234\",\"fullName\":\"Smoke Seller\",\"accountType\":\"SELLER\"}"
expect 201 "register seller (user-service)"
SELLER=$(field accessToken)

REMAINING=$(header X-RateLimit-Remaining)
[ -n "$REMAINING" ] && [ "$REMAINING" -ge 0 ] 2>/dev/null
check $? "auth route is rate limited by Redis (X-RateLimit-Remaining: ${REMAINING:-missing})" \
  "Missing header = limiter not applied; -1 = limiter could not reach Redis (fail-open)."

request POST /api/v1/auth/register "" "{\"email\":\"buyer$RUN@test.com\",\"password\":\"Buyer1234\",\"fullName\":\"Smoke Buyer\"}"
expect 201 "register buyer (user-service)"
BUYER=$(field accessToken)

PRODUCT_JSON='{"name":"Smoke Test Keyboard","description":"Created by smoke-test.sh","category":"Electronics","price":49.99,"stockQuantity":3}'
request POST /api/v1/products "$BUYER" "$PRODUCT_JSON"
expect 403 "buyer cannot create products (RBAC in product-service)"

request POST /api/v1/products "$SELLER" "$PRODUCT_JSON"
expect 201 "seller creates product with stock 3 (product-service)"
PRODUCT_ID=$(field id)
CACHE_KEY="shopflow:v1:products::$PRODUCT_ID"

request GET "/api/v1/products/$PRODUCT_ID"
expect 200 "anyone can view the product"

[ "$(redis-cli --raw exists "$CACHE_KEY")" = "1" ]
check $? "product read was cached in Redis ($CACHE_KEY)" "Is product-service connected to Redis? See logs/product-service.log"

TTL=$(redis-cli --raw ttl "$CACHE_KEY")
[ "$TTL" -gt 0 ] 2>/dev/null
check $? "cache entry has a TTL (${TTL}s left)"

UPDATED_JSON='{"name":"Smoke Test Keyboard v2","description":"Updated by smoke-test.sh","category":"Electronics","price":59.99,"stockQuantity":3}'
request PUT "/api/v1/products/$PRODUCT_ID" "$SELLER" "$UPDATED_JSON"
expect 200 "seller updates the product price"

[ "$(redis-cli --raw exists "$CACHE_KEY")" = "0" ]
check $? "update evicted the cached product"

request GET "/api/v1/products/$PRODUCT_ID"
PRICE=$(field price)
[ "$PRICE" = "59.99" ]
check $? "next read shows the new price, not a stale cached one (got '$PRICE')"

request POST /api/v1/cart/items "$BUYER" "{\"productId\":$PRODUCT_ID,\"quantity\":2}"
expect 200 "buyer adds 2 to cart (order-service -> product-service)"

request POST /api/v1/cart/items "$BUYER" "{\"productId\":$PRODUCT_ID,\"quantity\":5}"
expect 409 "adding more than available stock is rejected"

request POST /api/v1/orders "$BUYER"
expect 201 "buyer places order (stock reserved remotely)"
ORDER_ID=$(field id)
ORDER_STATUS=$(field status)
[ "$ORDER_STATUS" = "CONFIRMED" ]
check $? "order status is CONFIRMED (got '$ORDER_STATUS')"

# The product was cached with stock 3 just before the order. Seeing 1 here proves the
# reservation evicted the cache entry.
request GET "/api/v1/products/$PRODUCT_ID"
STOCK=$(field stockQuantity)
[ "$STOCK" = "1" ]
check $? "stock went from 3 to 1 (reservation evicted the cached product; got '$STOCK')"

request POST /api/v1/orders "$BUYER"
expect 400 "ordering with an empty cart is rejected"

request GET "/internal/products?ids=$PRODUCT_ID"
expect 404 "internal endpoints are NOT reachable through the gateway"

request POST "/api/v1/orders/$ORDER_ID/cancel" "$BUYER"
expect 200 "buyer cancels the order"

request GET "/api/v1/products/$PRODUCT_ID"
STOCK=$(field stockQuantity)
[ "$STOCK" = "3" ]
check $? "cancel returned stock and evicted the cache (back to 3; got '$STOCK')"

request POST "/api/v1/orders/$ORDER_ID/cancel" "$BUYER"
expect 409 "cancelling twice is rejected"

# Rate limit: fire 40 requests at the same moment as the buyer. Their bucket holds 20
# tokens, so roughly half must be rejected. A fresh buyer per run = a fresh bucket.
BURST=40
for i in $(seq 1 $BURST); do
  curl -s -o "$TMP/body.$i" -w "%{http_code}" -H "Authorization: Bearer $BUYER" \
    "$BASE/api/v1/users/me" > "$TMP/code.$i" &
done
wait
ALLOWED=0; LIMITED=0; LIMITED_BODY=""
for i in $(seq 1 $BURST); do
  code=$(cat "$TMP/code.$i")
  if [ "$code" = "200" ]; then ALLOWED=$((ALLOWED + 1)); fi
  if [ "$code" = "429" ]; then LIMITED=$((LIMITED + 1)); LIMITED_BODY="$TMP/body.$i"; fi
done
[ "$ALLOWED" -gt 0 ] && [ "$LIMITED" -gt 0 ]
check $? "burst of $BURST parallel requests: $ALLOWED allowed, $LIMITED rejected with 429 (per-user token bucket)"

if [ -n "$LIMITED_BODY" ]; then
  grep -q '"status":429' "$LIMITED_BODY"
  check $? "429 response uses the standard JSON error format"
fi

echo ""
if [ "$FAILED" = "0" ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED (see above)"; exit 1; fi
