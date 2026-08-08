// The vendored bundle for blog's Zen writing mode.
//
// This exposes CodeMirror's primitives and nothing else. The key scheme lives
// in blog's inline script in views.clj, the same way tracker's lives in
// et.tr.ui.codemirror rather than in a library — so the bindings can be
// changed without npm, which the devbox cannot reach.
//
// Rebuild after touching this file (host only, npm is not reachable in the box):
//
//     cd blog/scripts/zen-editor && npm install && npm run build
//
// The output at resources/public/blog/vendor/codemirror/codemirror.js is
// committed, like vendor/hljs, so running blog needs no build step.

import { EditorState } from "@codemirror/state";
import { EditorView, keymap } from "@codemirror/view";
import * as commands from "@codemirror/commands";

window.CM6 = { EditorState, EditorView, keymap, commands };
