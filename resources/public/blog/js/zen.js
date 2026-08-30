(function(){
var openBtn=document.getElementById('zen-open');
if(!openBtn)return;
var overlay=document.getElementById('zen-overlay'),
    mount=document.getElementById('zen-content'),
    content=document.getElementById('content'),
    form=content.form,
    flash=document.getElementById('save-flash'),
    palette=document.querySelector('.symbol-palette'),
    view=null;

/* ---- the editor ------------------------------------------------------- */

/* Themed to blog's prose rather than to a code editor: the page's family, size
   and line-height, no gutters, no border, caret in the text colour. It should
   read as the same page as #content, only bigger. */
function makeView(doc){
  var CM=window.CM6,c=CM.commands;
  var theme=CM.EditorView.theme({
    '&':{height:'100%',fontFamily:'inherit',fontSize:'1.125rem',color:'inherit',backgroundColor:'transparent'},
    '&.cm-focused':{outline:'none'},
    '.cm-scroller':{overflow:'auto',fontFamily:'inherit',lineHeight:'1.8'},
    '.cm-content':{padding:'0',fontFamily:'inherit',caretColor:'rgba(0,0,0,0.8)'},
    '.cm-line':{padding:'0'},
    '.cm-gutters':{display:'none'},
    '.cm-activeLine':{backgroundColor:'transparent'},
    '.cm-cursor':{borderLeftColor:'rgba(0,0,0,0.8)'}
  });
  /* history() and historyKeymap are not optional: a bare EditorView has no undo
     at all, and losing cmd+Z would be a regression against the textarea this
     replaces. defaultKeymap carries Enter, Backspace and the arrows.
     lineWrapping is what makes cursorLineUp/Down move by *visual* line, which is
     the reason this is CodeMirror and not a textarea. */
  var state=CM.EditorState.create({doc:doc,extensions:[
    theme,
    CM.EditorView.lineWrapping,
    c.history(),
    CM.keymap.of(c.historyKeymap),
    CM.keymap.of(c.defaultKeymap)
  ]});
  return new CM.EditorView({state:state,parent:mount});
}

/* ---- Daniel's markdown motion scheme --------------------------------- */

/* Not blog's any more. The scheme - the eight chords, the sentence motions they
   move by, and the capture-phase listener that makes them win over CodeMirror's
   own keymaps - lives in keyboard-wizardry/codemirror, beside the vscode/ and
   obsidian/ folders of the same scheme, and comes in through the vendored bundle
   as window.IJKL. It is tested there, in node and in a browser both. Blog keeps
   what is blog's: the theme, the overlay, the saving. */
function bindKeys(view){
  window.IJKL.install(view,window.CM6.commands);
}

/* ---- opening and closing --------------------------------------------- */

function isOpen(){return overlay.style.display!=='none';}

/* #content is itself an editor now, mounted by editors.js. Zen hands its text
   back and forth with *that*, not with the textarea underneath it: the textarea
   is only a mirror of the inline editor's document, so writing to it directly
   would leave the inline editor showing the text from before Zen was opened, and
   the next keystroke in it would put that stale text straight back.

   Still written to fall back to the textarea. editors.js does nothing on a page
   with no marked textareas, and Zen should not be the thing that breaks if this
   page ever becomes one of those. */
function inline(){
  return window.BlogEditors?window.BlogEditors.get('content'):null;
}

function setCaret(pos){
  var at=Math.max(0,Math.min(pos,view.state.doc.length));
  view.dispatch({selection:{anchor:at,head:at},scrollIntoView:true});
}

/* The view is built on first open, not at load: CodeMirror measures itself, and
   measuring inside a display:none overlay gives it nothing to measure. */
function open(){
  var source=inline(),
      doc=source?source.state.doc.toString():content.value,
      caret=source?source.state.selection.main.head:(content.selectionStart||0);
  overlay.style.display='block';
  document.body.style.overflow='hidden';
  if(!view){view=makeView(doc);bindKeys(view);}
  else{view.dispatch({changes:{from:0,to:view.state.doc.length,insert:doc}});}
  setCaret(caret);
  view.focus();
}

/* Read out of the view at the two moments that matter - on save and here -
   rather than syncing as you type. The caret comes back out of Zen with the
   text, so closing leaves you where you were writing. */
function close(){
  var text=view?view.state.doc.toString():content.value,
      caret=view?view.state.selection.main.head:0,
      at=Math.max(0,Math.min(caret,text.length)),
      target=inline();
  overlay.style.display='none';
  document.body.style.overflow='';
  if(target){
    /* Changes and selection in one transaction: an explicit selection is read
       against the document the transaction produces, not the one it replaced. */
    target.dispatch({changes:{from:0,to:target.state.doc.length,insert:text},
                     selection:{anchor:at,head:at},
                     scrollIntoView:true});
    target.focus();
  }else{
    content.value=text;
    content.focus();
    content.selectionStart=content.selectionEnd=at;
  }
}

/* ---- the in-between save --------------------------------------------- */

/* A fresh node, so a second save restarts the animation instead of being swallowed. */
function mark(ok){while(flash.firstChild)flash.removeChild(flash.firstChild);
  var s=document.createElement('span');
  s.className='save-flash-mark'+(ok?'':' failed');
  s.textContent=ok?'✓':'✗';
  flash.appendChild(s);}

/* FormData leaves out submit buttons that were not clicked, so publish and
   save-version stay out of an in-between save. Only 204 is success: a stale
   session answers with a redirect that fetch follows to a 200 login page. */
function save(){var fd=new FormData(form);
  fd.set('content',view?view.state.doc.toString():content.value);
  fd.set('no-redirect','1');
  /* Submitting a form normalizes textarea newlines to CRLF, FormData does not,
     and neither does doc.toString(). Without doing it by hand an in-between
     save would rewrite the line endings of every multiline field, and the next
     version bump would see a change nobody made and act on it. (Keep the button
     labels out of this comment: one edit-page test greps the body for them.) */
  var body=new URLSearchParams();
  fd.forEach(function(v,k){body.append(k,String(v).replace(/\r\n|\r|\n/g,'\r\n'));});
  fetch(form.action,{method:'POST',body:body})
    .then(function(r){mark(r.status===204);})
    .catch(function(){mark(false);});}

/* ---- wiring ---------------------------------------------------------- */

openBtn.addEventListener('click',open);
document.getElementById('zen-close').addEventListener('click',close);

/* No Escape handler on purpose: the X is the only way out, and a stray Escape
   while writing must not throw you out. */
document.addEventListener('keydown',function(e){
  if(!isOpen())return;
  /* `e.code` for the digit: a modifier can change what a key *is*, so â9 does
     not necessarily arrive as "9", while Digit9 names the physical key whatever
     is held down with it - the rule claude-coordinator's ui/keys.cljs states.
     `e.key` stays accepted so nothing that worked before stops working. */
  if((e.metaKey&&(e.code==='Digit9'||e.key==='9'))||
     ((e.metaKey||e.ctrlKey)&&e.key==='Enter')){e.preventDefault();save();}
});

/* The palette collects document.querySelectorAll('textarea') at load, and
   CodeMirror is a contenteditable rather than a textarea - so while Zen is open
   its clicks would insert into whichever hidden background textarea was focused
   last. Capture on the parent runs before the target-phase listener on the
   button, so stopPropagation here means Daniel's never fires and his script
   stays untouched. */
if(palette){
  palette.addEventListener('click',function(e){
    if(!isOpen()||!view)return;
    var button=e.target.closest('button[data-symbol]');
    if(!button)return;
    e.stopPropagation();
    var symbol=button.getAttribute('data-symbol'),
        selection=view.state.selection.main,
        at=selection.from+symbol.length;
    view.dispatch({changes:{from:selection.from,to:selection.to,insert:symbol},
                   selection:{anchor:at,head:at}});
    view.focus();
  },true);
}
})();
