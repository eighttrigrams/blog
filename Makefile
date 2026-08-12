.PHONY: restart deploy backup backup-replay test

# Kills by what it is, not by where it listens. It used to kill port 3028 —
# blog's port back when it ran on its own, and still the default in
# scripts/start.sh — while the server has listened on config.edn's 3130 since
# blog joined the plurama umbrella. So it killed nothing, the new JVM died with
# "Address in use", and the old one carried on serving, which reads exactly like
# a restart that worked.
#
# The brackets are not decoration. `pkill -f` matches against whole command
# lines, and this recipe is itself a command line containing the pattern — so a
# plain 'et.blog.server' matches the shell running the recipe and can kill it
# before it ever starts the server. That is not hypothetical: it is what
# handoffs/blog-scoping-report.md hit with plurama's own `make stop`, and pkill
# excludes only itself, not its parent. '[e]t.blog.server' as a *regex* does not
# match the literal text '[e]t.blog.server' in this line, but does match
# 'et.blog.server' in the JVM's arguments. Verified both ways with pgrep.
restart:
	@pkill -f '[e]t.blog.server' 2>/dev/null; sleep 1 && DEV=true clj -M -m et.blog.server &

test:
	clojure -M:test

# There is no test-js target any more. The Zen editor's markdown motions moved
# out to keyboard-wizardry/codemirror, and their tests went with them - `npm test`
# and `npm run e2e` in that folder. What is left here of Zen is theme and
# plumbing, which the clj tests cover.
