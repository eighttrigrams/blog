# blog

Server-rendered blog: articles with versions, announcement posts, comments and
replies. `make restart` starts the dev server on the port in `config.edn`,
`make test` runs the tests.

## API

A read-only JSON API under `/api`. It exposes exactly what an anonymous visitor
can already read — published articles, their published versions and their
comment threads. There are no drafts, no deleted articles, no auth and no
mutations, and anything unpublished or unknown answers `404 {"error": "Not
found"}` so the two are indistinguishable.

| Endpoint | What it serves |
| --- | --- |
| `GET /api/describe` | The API describing itself: method, path and docstring per route |
| `GET /api/articles` | The published articles, latest announcement first, without the landing article the public home page also leaves out |
| `GET /api/articles/:id` | One published article at its current version, metadata and content |
| `GET /api/articles/:id/versions` | Its published versions, newest first, one entry per version |
| `GET /api/articles/:id/versions/:version` | One published version, metadata and content |
| `GET /api/articles/:id/comments` | Every comment on the article, newest first, replies nested |
| `GET /api/articles/:id/versions/:version/comments` | The comments on that one version, newest first, replies nested |

Content comes back the way the data model stores it — the markdown source the
HTML pages render from, not the rendered HTML.

The handlers live in `et.blog.handler.api`, and their docstrings are the
documentation `/api/describe` hands out. Which articles, versions and comments a
reader may see is decided in `et.blog.handler.visibility`, which the HTML
handlers read through as well, so the API cannot drift from the pages.
