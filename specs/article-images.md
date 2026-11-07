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

  Scenario: Not a picture
    When I drop "notes.pdf" or "drawing.svg"
    Then nothing is uploaded
    And a line in the text says why, where I dropped it

  Scenario: The upload fails
    When the upload fails
    Then the placeholder becomes a line saying what went wrong
    And the picture is never dropped silently

  Scenario: Dev has no upload
    Given the server has no FTP credentials, as in development
    When I drop a picture
    Then the placeholder becomes "Image upload is not configured on this server."

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
