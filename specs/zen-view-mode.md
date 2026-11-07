# Zen: Edit Mode and View Mode

## Overview

Zen gets a second face. **Edit mode** is Zen as it is today, unchanged.
**View mode** shows the same text rendered, the way the article page renders
it. Escape goes from edit to view, and a click on the rendering goes back into
the text, at the place clicked. Only the X leaves Zen.

The model is zoo's markdown pane (`zoo/src/cljs/et/zoo/ui/views/editor.cljs`):
a click on a rendering lands in the source where you pointed
(`et.zoo.ui.md-point`), the rendered elements say which source line they came
from (`et.zoo.ui.markdown`, `data-line`), and the editor stays mounted
underneath, so the caret and the undo history survive a look at the rendering.

## Behaviour

```gherkin
Feature: Zen opens in edit mode

  Scenario: Opening Zen
    When I click "Zen" on an article's edit page
    Then Zen opens in edit mode
    And it looks and works exactly as it does today

Feature: Escape shows the rendering

  Scenario: From edit to view
    Given I am in Zen's edit mode
    When I press Escape
    Then Zen shows the text rendered as the article page renders it:
      footnotes numbered, citations quoted, images shown
    And it renders what is in the editor, unsaved changes included
    And the line I left rings once
    And nothing is saved

  Scenario: Escape in view mode
    Given I am in Zen's view mode
    When I press Escape
    Then nothing happens, and I stay in Zen

Feature: A click goes back into the text

  Scenario: Clicking the rendering
    Given I am in Zen's view mode
    When I click a word in the rendering
    Then Zen is in edit mode
    And the caret is at that word in the markdown
    And that line is centred, and the caret pops

  Scenario: Selecting text to read
    Given I am in Zen's view mode
    When I select text with the mouse, or drag
    Then I stay in view mode

  Scenario: Clicking a link
    Given I am in Zen's view mode
    When I click a link
    Then I go into the text at the link, as with any other click
    And with ⌘ held, the link opens in a new tab instead

  Scenario: The editor remembers
    Given I edited something, pressed Escape, and clicked back in
    Then cmd+Z still undoes what I did before

Feature: Saving and leaving

  Scenario: cmd+9 in either mode
    When I press cmd+9 in edit mode or in view mode
    Then the article is saved, as cmd+9 saves in Zen today, with the ✓
    And I stay in the mode I was in

  Scenario: Leaving Zen
    When I click the X, in either mode
    Then Zen closes and hands its text back to the form, as today
    And Escape never closes Zen
```

## Notes for the implementation

- **The same rendering as the article page.** The server renders it with
  `et.blog.render`, the way the article page does: footnotes come from the
  form's footnotes field, citations are resolved, and `blog-images/` paths
  resolve through `image-base-url`. That needs an owner-only way to render
  unsaved text.
- **Click to text.** The rendering has to say where its blocks start in the
  source, and the place inside a block is found the way zoo's `md-point`
  finds it: the rendered characters are the source's characters in order,
  with the markers taken out. A citation's quoted text and a footnote's number
  are not in the source; a click there lands at the start of what produced
  them.
- **Dropping pictures** (see [article-images.md](article-images.md)) works in
  edit mode only.
