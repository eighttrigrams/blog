.PHONY: restart deploy backup backup-replay test

# The one way to run blog in development, whether it is running or not. It stops
# the server on config.edn's port, 3130 (the workspace README's port table is the
# authority), starts a new one with DEV=true, and returns once it listens. Its
# output goes to logs/dev.log. The how and why are in scripts/restart.sh.
restart:
	@./scripts/restart.sh

test:
	clojure -M:test

# There is no test-js target any more. The Zen editor's markdown motions moved
# out to keyboard-wizardry/codemirror, and their tests went with them - `npm test`
# and `npm run e2e` in that folder. What is left here of Zen is theme and
# plumbing, which the clj tests cover.
