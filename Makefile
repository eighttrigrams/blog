restart:
	@lsof -ti :3028 | xargs kill 2>/dev/null; sleep 1 && DEV=true clj -M -m et.blog.server &
