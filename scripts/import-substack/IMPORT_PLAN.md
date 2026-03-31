# Substack Import Plan

## Source
- Blog: eighttrigrams.substack.com
- **29 articles** total (May 2022 — Feb 2026), discovered via `/api/v1/archive` (paginated with offset)
- Imported in chronological order (oldest first)

## Approach
- Node.js script (`import.mjs`) fetches each article HTML from Substack
- Uses `cheerio` to parse HTML, `turndown` to convert to markdown
- Publishes via the blog's HTTP API (POST /articles) with `dangerously-skip-logins? true`
- Articles imported in chronological order (oldest first)

## Timestamp spacing
- Articles must be 2 minutes apart so they show distinct timestamps on the home page
- After import, fix in DB: `UPDATE articles SET created_at = ... WHERE article_id = N`
- Same for posts table
- Base time: 20:00:00, each +2 min (article 1 = 20:00, article 29 = 20:56)

## Content transformation
- Each article's first line: `*Originally published on Substack on [date]*` (italic) + two newlines
- Each article's accompanying post says: `Originally published on Substack on [date]`
- Substack footnote anchors → blog's `FOOTNOTE:fnN` system
- Substack footnote definitions → blog's `footnotes` field
- Images keep their Substack CDN URLs; outer `<a>` wrapper stripped so markdown `![](url)` works cleanly
- Straight quotes in titles/subtitles converted to curly quotes (Gänsefüßchen)
- Body text gets smart quotes via Flexmark TypographicExtension
- Subscription widgets / buttons stripped

## Blog bug found & fixed
The blog's `render.clj` had a rendering order bug:
1. `replace-footnote-refs` inserts raw HTML (`<sup>` tags)
2. `markdown->html` then escapes that HTML because `ESCAPE_HTML true`

**Fix:** Moved footnote ref replacement to AFTER markdown rendering.

Also fixed:
- Footnote definitions now rendered as markdown (were plain text)
- Footnote parser now handles multi-line definitions (split on `FOOTNOTE:` boundaries instead of per-line)

## How to run

```bash
# 1. Fresh DB: stop server, delete data/blog.db, restart server
# 2. Run import:
cd blog/scripts/import-substack
node import.mjs          # real import
node import.mjs --dry-run  # preview only
```

## Discovery
- RSS feed (`/feed`) only returns latest 5 articles
- Archive page (`/archive`) uses JS lazy-loading, hard to scrape
- **Substack API** (`/api/v1/archive?sort=new&limit=50&offset=N`) — must paginate to get all 29

## Status
- [x] Script written and tested (dry run)
- [x] Footnote rendering bug in render.clj fixed (3 changes)
- [x] First import (5 articles) — verified footnotes + italic line
- [x] Full article list discovered via Substack API (29 articles, paginated)
- [x] Full import of all 29 articles complete (articles 1-29, chronological)
- [x] Image link wrapper fix (stripped `<a>` wrapping `<img>`)
- [x] Smart/curly quotes via Flexmark TypographicExtension
- [ ] Verify images display (images use Substack CDN URLs)
- [ ] Check for any stray Substack widget HTML in content

## Intra-blog link rewriting
- All `eighttrigrams.substack.com/p/{slug}` links rewritten to `/articles/{id}` (relative paths)
- Slug → ID mapping applied to both `content` and `footnotes` columns via SQL REPLACE
- `superhuman-memory` (deleted/renamed slug) mapped to article 21 (Rhizome)
- After re-import, re-run the slug→ID replacements (or add to import script)

## Known limitations
- Images still reference Substack CDN — if Substack goes away, images break
