.PHONY: restart deploy backup backup-replay test

# Kills by what it is, not by where it listens. It used to kill port 3028 —
# blog's port back when it ran on its own, and still the default in
# scripts/start.sh — while the server has listened on config.edn's 3130 since
# blog joined the plurama umbrella. So it killed nothing, the new JVM died with
# "Address in use", and the old one carried on serving, which reads exactly like
# a restart that worked. A pattern cannot go stale the way that literal did, and
# it is how plurama's own `make stop` does it.
restart:
	@pkill -f 'et.blog.server' 2>/dev/null; sleep 1 && DEV=true clj -M -m et.blog.server &

test:
	clojure -M:test

# There is no test-js target any more. The Zen editor's markdown motions moved
# out to keyboard-wizardry/codemirror, and their tests went with them - `npm test`
# and `npm run e2e` in that folder. What is left here of Zen is theme and
# plumbing, which the clj tests cover.
