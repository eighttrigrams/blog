# Substack Import Plan

## Source
- Blog: eighttrigrams.substack.com + eighttrigrams.net (GitHub)
- **29 articles** total (May 2022 — Feb 2026)
- Articles 1-2 (Clojure/Haskell tutorials): imported from local clone of eighttrigrams.net (`_posts/`)
- Articles 3-29: imported from Substack via HTML scraping
- Imported in chronological order (oldest first)

## Approach
- `import.mjs`: fetches articles, publishes via blog HTTP API
  - Articles 1-2: reads Jekyll markdown from local `_posts/` directory, strips frontmatter
  - Articles 3-29: fetches HTML from Substack, parses with `cheerio`, converts with `turndown`
- `upload-images.mjs`: downloads images, uploads to FTP, rewrites DB URLs
  - Content images: extracted from article content, uploaded to `blog-images/{id}/`
  - Preview/cover images: fetched from Substack API `cover_image` field, stored in `article_meta.preview_image`
  - Uses `better-sqlite3` for reliable URL replacement (avoids shell escaping issues with `$` in URLs)
- FTP credentials in `.ftp-credentials` (gitignored)

## Post-import DB fixups
- Timestamps: 2 minutes apart (base 20:00, article 1 = 20:00, article 29 = 20:56)
- Intra-blog links: `eighttrigrams.substack.com/p/{slug}` → `/articles/{id}`
- Preamble text: "Originally published on *eighttrigrams.substack.com* on [date]" (articles 3-29)
  - Articles 1-2: "Originally published on *eighttrigrams.net* on [date]"
- Post content matches preamble text

## Content transformation
- Preamble field (rendered italic via CSS) replaces inline "Originally published" line
- Substack footnote anchors → blog's `FOOTNOTE:fnN` system
- Substack footnote definitions → blog's `footnotes` field
- Images: relative paths (`blog-images/{id}/filename`) in DB, base URL prepended at render time via `:image-base-url` in config.edn
- Image `<a>` wrapper stripped so markdown `![](url)` works cleanly
- Straight quotes in titles/subtitles converted to curly quotes
- Body text gets smart quotes via Flexmark TypographicExtension
- Subscription widgets / buttons stripped

## Blog changes made
- **render.clj**: footnote replacement moved to after markdown rendering (was getting HTML-escaped)
- **render.clj**: footnote defs rendered as markdown; multi-line defs supported
- **render.clj**: TypographicExtension enabled for smart quotes
- **render.clj**: `resolve-image-paths` prepends configurable `image-base-url` to relative image paths
- **views.clj**: `img { max-width: 100%; height: auto }` for content images
- **views.clj**: removed double `escape-html` on titles/subtitles (Hiccup already escapes)
- **views.clj**: preamble field rendered in italic div above content
- **views.clj**: preview image field in edit form
- **views.clj**: preview image shown on home page article list
- **server.clj**: resolves preview image paths with configured base URL
- **config.edn**: `:image-base-url` setting
- **migration 008**: `preamble` column on articles table
- **migration 009**: `preview_image` column on article_meta table

## How to run

```bash
# 1. Fresh DB: stop server, delete data/blog.db, restart server
# 2. Import articles:
cd blog/scripts/import-substack
node import.mjs
# 3. Apply DB fixups (timestamps, intra-links, preambles) — see shell commands in conversation
# 4. Upload images + preview images:
node upload-images.mjs
```

## Image hosting
- Images hosted on FTP at daniel-de-oliveira.com/blog-images/{article-id}/
- Content images: `1-{hash}.{ext}`, `2-{hash}.{ext}`, ...
- Preview images: `preview-{hash}.{ext}`
- DB stores relative paths; `:image-base-url` in config.edn prepends the domain at render time

## Known limitations
- Articles 1-7 have no preview images (no cover images on Substack for those)
- Substack footnotes linking to other Substack articles (Personalist, Rhizome, etc.) rewritten to local `/articles/{id}` but those were separate essays, not the same articles
