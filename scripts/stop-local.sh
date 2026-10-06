#!/usr/bin/env bash
# Stops everything started by run-local.sh.
cd "$(dirname "$0")/.."
if [ ! -d .pids ]; then echo "Nothing to stop."; exit 0; fi
for pidfile in .pids/*.pid; do
  [ -e "$pidfile" ] || continue
  name=$(basename "$pidfile" .pid)
  pid=$(cat "$pidfile")
  if kill "$pid" 2>/dev/null; then echo "Stopped $name (pid $pid)"; else echo "$name was not running"; fi
  rm -f "$pidfile"
done
