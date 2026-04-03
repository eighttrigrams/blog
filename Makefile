.PHONY: restart deploy backup backup-replay test

restart:
	@lsof -ti :3028 | xargs kill 2>/dev/null; sleep 1 && DEV=true clj -M -m et.blog.server &

test:
	clojure -M:test

deploy: backup test
	fly deploy

backup:
	@mkdir -p .backups
	fly ssh console -C "tar -czf - /app/data" > .backups/volume-backup.$$(date +%Y-%m-%d.%H-%M).tar.gz

backup-replay:
	@if [ -d data ]; then echo "Error: data/ directory already exists. Remove it first." && exit 1; fi
	tar -xzf $$(ls -t .backups/volume-backup.*.tar.gz | head -1) --strip-components=1
