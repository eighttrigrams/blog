.PHONY: restart deploy backup backup-replay test

restart:
	@lsof -ti :3028 | xargs kill 2>/dev/null; sleep 1 && DEV=true clj -M -m et.blog.server &

test:
	clojure -M:test

.PHONY: test-js

# The Zen editor's markdown motions. node --test needs no packages, which is why
# these can be tested at all. A glob, not test/js/ - node reads a bare directory
# as a module to load, not as a tree to scan.
test-js:
	node --test "test/js/*_test.js"
