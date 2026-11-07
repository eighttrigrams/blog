# Versions and Revisions

## Overview

An article changes in two steps of different size:

- **Version** (v1, v2, …): the big step. "Save new version" starts one, and
  "Publish" announces it with a post and a mail to the subscribers. Comments
  belong to a version.
- **Revision**: the small step. Every save with changes, from the Save button
  or Zen's cmd+9, adds one. Readers can step through all of them with the ← →
  arrows next to the date. Edits stay transparent this way, including the ones
  made after a version was announced.

A draft is version 0 and nobody but the owner sees it. An article is public
from v1 on. "Publish" announces a version; it does not make it visible.

## Data

There is no versions table. Each revision is one row in `articles`, keyed by
`(article_id, created_at)`, and its `version` column says which version it
belongs to. A version is all the rows that share a number.

```
article_id | created_at          | version | content …
40         | 2026-10-01 10:00:00 | 0       | draft
40         | 2026-10-02 09:00:00 | 1       | v1 as announced
40         | 2026-10-20 18:00:00 | 1       | typo fix (revision)
40         | 2026-11-06 20:30:00 | 1       | Zen cmd+9 (revision)
```

Other tables refer to a version by its number only:

- `article_posts (article_id, article_version, post_id)`: the announcement, at
  most one per version. A version with one is "published".
- `comments (…, article_version)`.
- `article_meta`: one row per article (abstract, preview image, categories,
  deleted). Not versioned and not revisioned: a change there overwrites.

## Behaviour

```gherkin
Feature: Save new version

  Background:
    Given article "BDD & LLM" is at v2, and v2 is announced

  Scenario: Edits already saved as revisions, nothing new in the form
    Given I made revisions to v2 after its announcement
    And the form has no unsaved changes
    When I click "Save new version"
    Then the last revision moves from v2 to v3
    And v2 ends with the revision before it

  Scenario: Unsaved changes in the form
    Given I made revisions to v2 after its announcement
    And the form has unsaved changes
    When I click "Save new version"
    Then all v2 revisions stay in v2
    And my changes become the first revision of v3

  Scenario: Nothing changed since the announcement
    Given v2 has no revisions after its announcement
    And the form has no unsaved changes
    When I click "Save new version"
    Then it is refused: "Nothing changed since v2 was announced"
    And v2 stays as announced

  Scenario: Not twice in a row
    Given v3 exists and is not announced yet
    When I click "Save new version"
    Then it is refused: "Publish the current version before bumping to a new one."

Feature: Save new version on a draft

  Scenario: Draft, nothing new in the form
    Given article "BDD & LLM" is a draft (v0)
    And the form has no unsaved changes
    When I click "Save new version"
    Then the last draft revision becomes the first revision of v1

  Scenario: Draft with unsaved changes
    Given article "BDD & LLM" is a draft (v0)
    And the form has unsaved changes
    When I click "Save new version"
    Then my changes become the first revision of v1

Feature: Save (button or Zen cmd+9)

  Scenario: Something changed
    When I save with changes
    Then a new revision is added to the current version

  Scenario: Nothing changed
    When I save without changes
    Then nothing is written
```

## Why moving the boundary revision is safe

Moving a revision to the next version changes only its `version` field. Its
`created_at` and its text stay the same, so:

- Citations (`CITE:<id>:<datetime>:…`) and `as-of` links still find the same
  text. See [the citation spec](citation-and-footnote-feature.md).
- Nothing points at the row itself, because the other tables name a version
  number, not a revision.
- The announced text of a version never changes. A revision moves only if it
  was made *after* the announcement post (strictly later `created_at`). That is
  why the "Nothing changed since the announcement" case refuses, rather than
  moving the announced revision away.
- A version is never left empty. An announced version keeps at least its
  announced revision. A draft has no announcement, post or comments to lose.

Only the *last* revision moves. Earlier revisions after the announcement stay
in the old version, because they can be typo fixes that belong there.
