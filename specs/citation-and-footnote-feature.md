# Citation and Footnote Feature

## Overview

Article content is written in Markdown. Two additional mechanisms are available
inline: **footnotes** and **citations**. Both use stable identifiers that
survive text reshuffling.

## Footnotes

### Inline references

Place `FOOTNOTE:<id>` anywhere in the article content. The id is an arbitrary
alphanumeric string (plus `-` and `_`) invented by the author:

```
This is an important claim FOOTNOTE:abc123 that needs backing up.
```

### Definitions

The footnotes field (stored separately from content) contains one definition
per line:

```
FOOTNOTE:abc123 Smith et al., 2024 demonstrated this conclusively.
FOOTNOTE:xyz789 See appendix B for the full derivation.
```

### Rendering rules

1. Scan the content for all `FOOTNOTE:<id>` references, recording the order
   of first appearance.
2. Parse the footnotes field into an `{id -> text}` map.
3. Replace each inline reference with a numbered superscript link `[1]`, `[2]`,
   etc., numbered by occurrence order.
4. Append a "Footnotes" section at the bottom as an ordered list, in the same
   order.
5. Defined but not referenced footnotes are **omitted**.
6. Referenced but not defined footnotes render as `[MISSING: <id>]`.

Because the ids are position-independent, reordering paragraphs never breaks
the association between reference and definition.

## Citations

### Syntax

Place `CITE:<article_id>:<datetime>:<start>:<end>` in the content:

```
As discussed previously:

CITE:2:2026-03-29 16:49:42:100:200

This demonstrates the point.
```

Where:
- `article_id` — the numeric id of the cited article
- `datetime` — an as-of timestamp (`YYYY-MM-DD HH:MM:SS`) pinning the exact
  version
- `start:end` — character range (0-indexed) to extract from the cited
  article's content

### Rendering

The backend fetches the cited article version (using the same `as-of` lookup
that powers `/articles/:id/as-of/:datetime`), extracts characters
`start..end`, and renders the excerpt as a Markdown blockquote with an
attribution link:

```html
<blockquote>
  <p>...extracted text...</p>
  <p>— <a href="/articles/2/as-of/2026-03-29 16:49:42">Article Title</a></p>
</blockquote>
```

Because citations reference immutable article snapshots via `as-of`, the
character ranges remain stable regardless of later edits to the cited article.

If the cited article or version is not found, a `[Citation not found]` message
is rendered instead.

## Implementation

- `et.blog.render` — contains the rendering pipeline: footnote scanning,
  citation resolution, Markdown-to-HTML conversion (via flexmark), and
  footnote section generation.
- Content and addenda are both rendered as Markdown. Footnote processing
  applies to content only; addenda is plain Markdown.
- The server passes a `fetch-fn` to the renderer so it can resolve citations
  without depending on the DB layer directly.
