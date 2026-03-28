.PHONY: start stop build test deploy clean backup backup-replay

start:
	@if [ -f .env ]; then set -a && . ./.env && set +a; fi && ./scripts/start.sh

build:
	clj -T:build uber

test:
	clj -X:test

deploy: backup
	fly deploy

clean:
	rm -rf target

backup:
	@mkdir -p .backups
	fly ssh console -C "tar -czf - /app/data" > .backups/volume-backup.$$(date +%Y-%m-%d.%H-%M).tar.gz

backup-replay:
	@if [ -d data ]; then echo "Error: data/ directory already exists. Remove it first." && exit 1; fi
	tar -xzf $$(ls -t .backups/volume-backup.*.tar.gz | head -1) --strip-components=1
