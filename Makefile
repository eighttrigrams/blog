.PHONY: restart deploy backup backup-replay test

restart:
	@lsof -ti :3028 | xargs kill 2>/dev/null; sleep 1 && DEV=true clj -M -m et.blog.server &

test:
	clojure -M:test

# There is no test-js target any more. The Zen editor's markdown motions moved
# out to keyboard-wizardry/codemirror, and their tests went with them - `npm test`
# and `npm run e2e` in that folder. What is left here of Zen is theme and
# plumbing, which the clj tests cover.
