// Markdown "sentence" motions for the Zen editor: ctrl+j back, ctrl+l forward.
// Pure functions of (text, pos) -> pos. No CodeMirror, no DOM — that is what
// makes them testable, and test/js/zen_test.js is what tests them.
//
// Two definitions carry the whole scheme:
//
//   hard break  a line whose text ends in two or more spaces, markdown's line
//               break. The spaces must sit immediately before a newline, so the
//               last line of a document cannot have one.
//   block       a run of lines bounded by a blank line or by an end of the
//               document. A run of several blank lines separates just like one.
//
// This is deliberately not the punctuation rule from cljs-text-editor.

function lineStartsOf(text) {
  const starts = [0];
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '\n') starts.push(i + 1);
  }
  return starts;
}

function lineIndexAt(starts, pos) {
  let i = 0;
  while (i + 1 < starts.length && starts[i + 1] <= pos) i++;
  return i;
}

function isBlank(line) {
  return /^\s*$/.test(line);
}

// A blank line of two spaces is blank, not a hard break: it separates blocks.
function hasHardBreak(lines, i) {
  return i >= 0 && i < lines.length - 1 && !isBlank(lines[i]) && / {2,}$/.test(lines[i]);
}

// Where a sentence begins: the first line of a block, or a line the one above
// broke into with a hard break.
function beginsSentence(lines, i) {
  if (isBlank(lines[i])) return false;
  if (i === 0) return true;
  if (isBlank(lines[i - 1])) return true;
  return hasHardBreak(lines, i - 1);
}

function clamp(pos, text) {
  if (!(pos > 0)) return 0;
  return pos > text.length ? text.length : pos;
}

// ctrl+l. If this line ends in a hard break, the next line. Otherwise the start
// of the next block, past the blank lines rather than onto them. Past the last
// block, the end of the document.
function sentenceEnd(text, pos) {
  pos = clamp(pos, text);
  const lines = text.split('\n');
  const starts = lineStartsOf(text);
  const i = lineIndexAt(starts, pos);
  if (hasHardBreak(lines, i)) return starts[i + 1];
  let j = i;
  while (j < lines.length && !isBlank(lines[j])) j++;
  while (j < lines.length && isBlank(lines[j])) j++;
  return j < lines.length ? starts[j] : text.length;
}

// ctrl+j: the nearest sentence beginning strictly before pos, so that repeated
// presses walk backwards instead of jamming at a block start. That single rule
// gives both of the cases the scheme asks for — the line start when the line
// above ended in a hard break, the block start when it did not.
function sentenceStart(text, pos) {
  pos = clamp(pos, text);
  const lines = text.split('\n');
  const starts = lineStartsOf(text);
  for (let j = lineIndexAt(starts, pos); j >= 0; j--) {
    if (starts[j] < pos && beginsSentence(lines, j)) return starts[j];
  }
  return 0;
}

// Inert in the browser, which reads them off the global below; inert in node,
// which has no window.
if (typeof module !== 'undefined') module.exports = {sentenceStart, sentenceEnd};
if (typeof window !== 'undefined') window.ZenMotions = {sentenceStart, sentenceEnd};
