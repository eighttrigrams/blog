#!/bin/bash
set -e

if [ ! -f config.edn ]; then
  echo "Creating default config.edn..."
  cat > config.edn << 'EOF'
{:db {:type :sqlite-file
      :path "data/blog.db"}
 :port 3028
 :dangerously-skip-logins? true}
EOF
fi

PORT=$(bb -e '(:port (read-string (slurp "config.edn")))')
if [ -z "$PORT" ]; then
  echo "ERROR: Could not read :port from config.edn"
  exit 1
fi

echo "Starting server in development mode on port $PORT..."
DEV=true clojure -X:run
