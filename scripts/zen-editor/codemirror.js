// The vendored bundle for blog's Zen writing mode.
//
// This exposes CodeMirror's primitives and the IJKL bindings, and nothing else.
// The bindings are no longer blog's own: they live in
//
//     ~/Workspace/eighttrigrams/keyboard-wizardry/codemirror
//
// beside the vscode/ and obsidian/ folders of the same scheme, and are pulled in
// here as a `file:` dependency. That path is relative and assumes the two
// checkouts sit side by side under ~/Workspace, which is a fair assumption for a
// build that only ever runs on Daniel's host — see below.
//
// Rebuild after touching this file, or after changing the bindings in
// keyboard-wizardry (host only, npm is not reachable in the box):
//
//     cd blog/scripts/zen-editor && npm install && npm run build
//
// The output at resources/public/blog/vendor/codemirror/codemirror.js is
// committed, like vendor/hljs, so running blog needs no build step — and neither
// does deploying it. plurama/Dockerfile copies blog's deps.edn, build.clj, src
// and resources, runs no npm for blog at all, and so never needs to see
// keyboard-wizardry.

import { EditorState } from "@codemirror/state";
import { EditorView, keymap } from "@codemirror/view";
import * as commands from "@codemirror/commands";
import * as ijkl from "@eighttrigrams/kw-codemirror";

window.CM6 = { EditorState, EditorView, keymap, commands };
window.IJKL = ijkl;
