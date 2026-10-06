#!/usr/bin/env bash
# End-to-end check of the whole system THROUGH THE GATEWAY (port 8080).
# Uses fresh random emails each run, so it can be repeated.
set -uo pipefail
BASE=${BASE_URL:-http://localhost:8080}
RUN=$(date +%s)
FAILED=0
STATUS=""; BODY=""

request() { # method path [token] [json-body]
  local method=$1 path=$2 token=${3:-} body=${4:-} out
  local args=(-s -X "$method" "$BASE$path" -H "Content-Type: application/json" -w $'\n%{http_code}')
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

field() { # json-field-name -> value from $BODY (simple flat fields only)
  echo "$BODY" | sed -E 's/.*"'"$1"'":"?([^",}]*)"?.*/\1/'
}

echo "ShopFlow smoke test against $BASE"

request POST /api/v1/auth/register "" "{\"email\":\"seller$RUN@test.com\",\"password\":\"Seller1234\",\"fullName\":\"Smoke Seller\",\"accountType\":\"SELLER\"}"
expect 201 "register seller (user-service)"
SELLER=$(field accessToken)

request POST /api/v1/auth/register "" "{\"email\":\"buyer$RUN@test.com\",\"password\":\"Buyer1234\",\"fullName\":\"Smoke Buyer\"}"
expect 201 "register buyer (user-service)"
BUYER=$(field accessToken)

PRODUCT_JSON='{"name":"Smoke Test Keyboard","description":"Created by smoke-test.sh","category":"Electronics","price":49.99,"stockQuantity":3}'
request POST /api/v1/products "$BUYER" "$PRODUCT_JSON"
expect 403 "buyer cannot create products (RBAC in product-service)"

request POST /api/v1/products "$SELLER" "$PRODUCT_JSON"
expect 201 "seller creates product with stock 3 (product-service)"
PRODUCT_ID=$(field id)

request GET "/api/v1/products/$PRODUCT_ID"
expect 200 "anyone can view the product"

request POST /api/v1/cart/items "$BUYER" "{\"productId\":$PRODUCT_ID,\"quantity\":2}"
expect 200 "buyer adds 2 to cart (order-service -> product-service)"

request POST /api/v1/cart/items "$BUYER" "{\"productId\":$PRODUCT_ID,\"quantity\":5}"
expect 409 "adding more than available stock is rejected"

request POST /api/v1/orders "$BUYER"
expect 201 "buyer places order (stock reserved remotely)"
ORDER_ID=$(field id)
ORDER_STATUS=$(field status)
if [ "$ORDER_STATUS" = "CONFIRMED" ]; then echo "  PASS  order status is CONFIRMED"; else echo "  FAIL  order status is '$ORDER_STATUS'"; FAILED=1; fi

request GET "/api/v1/products/$PRODUCT_ID"
STOCK=$(field stockQuantity)
if [ "$STOCK" = "1" ]; then echo "  PASS  stock went from 3 to 1"; else echo "  FAIL  expected stock 1, got '$STOCK'"; FAILED=1; fi

request POST /api/v1/orders "$BUYER"
expect 400 "ordering with an empty cart is rejected"

request GET "/internal/products?ids=$PRODUCT_ID"
expect 404 "internal endpoints are NOT reachable through the gateway"

request POST "/api/v1/orders/$ORDER_ID/cancel" "$BUYER"
expect 200 "buyer cancels the order"

request GET "/api/v1/products/$PRODUCT_ID"
STOCK=$(field stockQuantity)
if [ "$STOCK" = "3" ]; then echo "  PASS  cancel returned stock (back to 3)"; else echo "  FAIL  expected stock 3, got '$STOCK'"; FAILED=1; fi

request POST "/api/v1/orders/$ORDER_ID/cancel" "$BUYER"
expect 409 "cancelling twice is rejected"

echo ""
if [ "$FAILED" = "0" ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED (see above)"; exit 1; fi
