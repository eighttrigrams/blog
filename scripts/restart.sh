#!/bin/bash
# Restart blog's dev server, whether or not one is running:
#   1. stop the JVM listening on blog's port, and wait until the port is free,
#   2. start a new one with DEV=true in the background, output to logs/dev.log,
#   3. wait until it listens, and print its pid.
# It finds the old server by its port, not its name: BusyBox pkill cannot see
# the namespace at the end of a JVM's long command line (cookbook #220). lsof
# reads ports the same in the box and on the Mac.
set -eu
cd "$(dirname "$0")/.."

# The port the server binds: PORT if set (config.edn reads it), else the
# first number after :port in config.edn.
port=${PORT:-$(grep -Eo ':port[^0-9]*[0-9]+' config.edn | head -1 | grep -Eo '[0-9]+$' || true)}
if [ -z "$port" ]; then
  echo "restart: found no :port in config.edn" >&2
  exit 1
fi
log=logs/dev.log

listeners() { lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | sort -u; }

old=$(listeners)
if [ -n "$old" ]; then
  # Only ever a java. On the Mac, Docker's port forwarding for a box listens
  # here too, and killing it cuts the box off.
  others=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null \
             | awk 'NR > 1 && $1 != "java" {print $1 " (pid " $2 ")"}' | sort -u)
  if [ -n "$others" ]; then
    echo "restart: :$port is held by something other than blog: $others" >&2
    echo "         If that is Docker, a box publishes this port: run blog in there." >&2
    exit 1
  fi
  echo "Stopping pid" $old "on :$port"
  kill $old
  for _ in $(seq 1 30); do [ -z "$(listeners)" ] && break; sleep 1; done
  if [ -n "$(listeners)" ]; then
    kill -9 $old 2>/dev/null || true
    sleep 2
  fi
  if [ -n "$(listeners)" ]; then
    echo "restart: :$port is still taken after stopping pid" $old >&2
    exit 1
  fi
fi

mkdir -p logs
echo "Starting blog (DEV=true) on :$port, log in blog/$log"
# setsid keeps it alive whatever happens to the shell that started it. macOS
# has none, and nohup is enough there.
detach=$(command -v setsid || true)
DEV=true nohup $detach clojure -X:run > "$log" 2>&1 < /dev/null &
pid=$!

for _ in $(seq 1 120); do
  new=$(listeners)
  if [ -n "$new" ]; then
    echo "Blog runs: pid $new, http://localhost:$port"
    exit 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "restart: the server exited. End of $log:" >&2
    tail -n 20 "$log" >&2
    exit 1
  fi
  sleep 1
done
echo "restart: nothing listens on :$port after 120s, see $log" >&2
exit 1
