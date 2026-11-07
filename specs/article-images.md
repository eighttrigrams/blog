# Images in an Article, by Drag and Drop

## Overview

Drop or paste a picture into an article's **content**, on the edit page or in
Zen, and it is uploaded into the article's own folder on the webspace. A link
to it goes into the text where it was dropped. It works like GitHub, and like
tracker's description field (`tracker/src/cljs/et/tr/ui/components/description_field.cljs`
and `image_prep.cljs`).

The folder is `blog-images/<article-id>/`, where article images have always
lived, so nothing has to be copied around later. Because the folder needs the
article's id, the content field only appears once the article exists.

Only the content field takes pictures. Footnotes, addenda and abstract do not.

## Behaviour

```gherkin
Feature: A new article has no content field yet

  Scenario: Creating an article
    When I open "New Article"
    Then there is no content field
    And when I create the article
    Then I am on its edit page, and the content field is there

Feature: Dropping or pasting a picture into the content

  Background:
    Given article 41 exists, and I am on its edit page or in Zen

  Scenario: A picture is dropped
    When I drop "Cinching Corset.jpg" into the content
    Then "![Uploading Cinching Corset.jpg…]()" appears where I dropped it
    And the picture is uploaded to "blog-images/41/"
    And the placeholder becomes "![Cinching Corset](blog-images/41/cinching-corset.jpg)"

  Scenario: A picture is pasted
    When I paste a screenshot into the content
    Then it goes the same way as a dropped picture, at the caret

  Scenario: Text is pasted
    When I paste text
    Then it is pasted as text, as ever

  Scenario: The name is taken
    Given "blog-images/41/cinching-corset.jpg" already exists
    When I drop another "Cinching Corset.jpg"
    Then it is stored as "cinching-corset-2.jpg"
    And the existing file is not overwritten

  Scenario: The folder cannot be read
    Given the server will not list "blog-images/41/" right now
    When I drop "Cinching Corset.jpg"
    Then it is stored under a name that cannot exist yet,
      e.g. "cinching-corset-20261107-201512.jpg"
    And it is written only once the server says that exact name is free
    And no existing file is overwritten

  Scenario: An odd name
    When I drop "日本.jpg" or "a.php.jpg"
    Then it is stored as "image.jpg" or "a-php.jpg":
      always with its extension, and with no dot but the last

  Scenario: Not a picture
    When I drop "notes.pdf" or "drawing.svg"
    Then nothing is uploaded
    And a line in the text says why, where I dropped it

  Scenario: The upload fails
    When the upload fails
    Then the placeholder becomes a line saying what went wrong, naming the file:
      "⚠ Cinching Corset.jpg not uploaded: <reason>"
    And the picture is never dropped silently

  Scenario: The placeholder is gone when the upload ends
    Given I undid the paste, or typed inside the placeholder
    When the upload ends
    Then its link (or its ⚠ line) goes in at the caret instead

  Scenario: Dev has no upload
    Given the server has no FTP credentials, as in development
    When I drop a picture
    Then the line reads "⚠ Cinching Corset.jpg not uploaded: Image upload is not configured on this server."

  Scenario: Saving waits for its pictures
    Given an upload is still running
    When I save (button, "Save new version", "Publish" or Zen's cmd+9)
    Then nothing is saved
    And I am told to wait for the upload

Feature: The picture loses its metadata, not its size

  Scenario: A photo
    When I drop a JPEG, PNG or WebP
    Then it is decoded the right way up and encoded again in the same format, at full size
    And its EXIF block (GPS position, camera, time) is gone

  Scenario: Another photo format
    When I drop an AVIF or HEIC the browser can decode
    Then it is uploaded as a JPEG, at full size, without EXIF

  Scenario: A GIF
    When I drop a GIF
    Then it is uploaded as it is, so an animation keeps its frames

  Scenario: An animated WebP or PNG
    When I drop one
    Then only its first frame goes up, since it is re-encoded like a still
```

## Rules

- Allowed: jpg, jpeg, png, gif, webp, avif (and HEIC as input, which goes up
  as JPEG). No SVG, which would be a script host on daniel-de-oliveira.com.
  These are the rules `et.blog.images` already applies to posts.
- The upload is owner-only, and the folder is a constant prefix plus the
  article's integer id. As with posts, nothing can steer the path.
- The size limit is the posts' one (`images/max-bytes`), applied to the bytes
  after the metadata is stripped. Over it, the upload is refused in words.
- The link in the text is the relative `blog-images/…` path. Rendering already
  puts `image-base-url` in front of it.
- **An extra file is fine, a lost one is not** (the human's rule). The upload
  never overwrites and never deletes a file it is not sure it made. When the
  server's answer is unclear (a refused listing, a cut-off one), it takes a
  name that cannot exist yet rather than guess. A half-written file is cleaned
  up only when that name was known to be new; otherwise it is left.
- JPEG and WebP are re-encoded lossily (quality 0.92). PNG stays lossless.

## Testing

As with the post image upload (August 2026):

1. **clj tests, no network:** the uploader is left unconfigured. They cover the
   guards (path, types, owner-only, the article id, no overwrite, refusing
   loudly when unconfigured), with the FTP side faked where the happy path
   needs it.
2. **One real round from the Mac**, with the real FTP credentials, into a
   folder no prod article uses. Then delete what was uploaded.
3. **Then in production.**

Development never uploads.
