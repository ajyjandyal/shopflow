#!/usr/bin/env bash
# Builds all services and starts them in the background.
# Logs: logs/<service>.log     Stop everything: ./scripts/stop-local.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then echo "ERROR: .env not found. Copy .env.example to .env and fill it in."; exit 1; fi
if ! grep -q '^INTERNAL_API_KEY=.\{32,\}' .env; then
  echo "ERROR: INTERNAL_API_KEY (32+ chars) missing in .env. Run:"
  echo '  echo "INTERNAL_API_KEY=$(openssl rand -hex 32)" >> .env'
  exit 1
fi

# Phase 3: Redis backs the product cache and the gateway's rate limiter.
REDIS_HOST_CHECK=${REDIS_HOST:-localhost}
REDIS_PORT_CHECK=${REDIS_PORT:-6379}
if ! command -v redis-cli >/dev/null 2>&1; then
  echo "ERROR: redis-cli not found. Install and start Redis:"
  echo "  brew install redis && brew services start redis"
  exit 1
fi
if [ "$(redis-cli -h "$REDIS_HOST_CHECK" -p "$REDIS_PORT_CHECK" ping 2>/dev/null)" != "PONG" ]; then
  echo "ERROR: Redis is not answering on $REDIS_HOST_CHECK:$REDIS_PORT_CHECK. Start it with:"
  echo "  brew services start redis"
  exit 1
fi

SERVICES="user-service:8081 product-service:8082 order-service:8083 api-gateway:8080"

for entry in $SERVICES; do
  port=${entry##*:}
  if lsof -ti tcp:"$port" >/dev/null 2>&1; then
    echo "ERROR: port $port is already in use. Stop whatever is running there"
    echo "(e.g. the Phase 1 app: Ctrl+C in its Terminal tab, or ./scripts/stop-local.sh) and try again."
    exit 1
  fi
done

echo "==> Building all services (first build downloads libraries, be patient)..."
mvn -q -f services/pom.xml -DskipTests package

mkdir -p logs .pids
for entry in $SERVICES; do
  name=${entry%%:*}; port=${entry##*:}
  nohup java -jar "services/$name/target/$name.jar" > "logs/$name.log" 2>&1 &
  echo $! > ".pids/$name.pid"
  echo "==> Started $name on port $port (log: logs/$name.log)"
done

echo "==> Waiting for services to become healthy..."
all_up=true
for entry in $SERVICES; do
  name=${entry%%:*}; port=${entry##*:}
  up=false
  for _ in $(seq 1 60); do
    if curl -sf "http://localhost:$port/actuator/health" >/dev/null 2>&1; then up=true; break; fi
    sleep 2
  done
  if $up; then echo "    $name UP"; else echo "    $name DID NOT START - check logs/$name.log"; all_up=false; fi
done

if $all_up; then
  echo ""
  echo "All services running. Front door: http://localhost:8080"
  echo "Swagger UIs: user :8081  product :8082  order :8083  (path /swagger-ui.html)"
  echo "Run the end-to-end check:  ./scripts/smoke-test.sh"
else
  echo ""
  echo "Some services failed. Look at the last lines of the log, e.g.:  tail -50 logs/order-service.log"
  exit 1
fi
