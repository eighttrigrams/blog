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

/* Keyed on e.code, never e.key: on macOS Option is a compose modifier, so
   option+j arrives as e.key "∆". An e.key map would fail silently for
   exactly the two wordwise bindings and look fine everywhere else. */
function chord(e){
  var mods=[];
  if(e.altKey)mods.push('alt');
  if(e.ctrlKey)mods.push('ctrl');
  if(e.metaKey)mods.push('meta');
  if(e.shiftKey)mods.push('shift');
  return e.code+' '+mods.join('+');
}

/* A sentence motion as a CodeMirror command: the pure function decides, this
   only moves the caret there. */
function motion(fn){
  return function(view){
    var target=fn(view.state.doc.toString(),view.state.selection.main.head);
    view.dispatch({selection:{anchor:target,head:target},scrollIntoView:true});
    return true;
  };
}

function keyCommands(){
  var c=window.CM6.commands,m=window.ZenMotions;
  return {
    'KeyI meta':c.cursorLineUp,
    'KeyK meta':c.cursorLineDown,
    'KeyJ meta':c.cursorCharLeft,
    'KeyL meta':c.cursorCharRight,
    'KeyJ alt':c.cursorGroupLeft,
    'KeyL alt':c.cursorGroupRight,
    'KeyJ ctrl':motion(m.sentenceStart),
    'KeyL ctrl':motion(m.sentenceEnd)
  };
}

/* Capture phase on the editor element, the way tracker's codemirror.cljs does
   it, so these win before CodeMirror's own keymaps see the event. */
function bindKeys(view){
  var commands=keyCommands();
  view.dom.addEventListener('keydown',function(e){
    var command=commands[chord(e)];
    if(!command)return;
    e.preventDefault();
    e.stopPropagation();
    command(view);
  },true);
}

/* ---- opening and closing --------------------------------------------- */

function isOpen(){return overlay.style.display!=='none';}

function setCaret(pos){
  var at=Math.max(0,Math.min(pos,view.state.doc.length));
  view.dispatch({selection:{anchor:at,head:at},scrollIntoView:true});
}

/* The view is built on first open, not at load: CodeMirror measures itself, and
   measuring inside a display:none overlay gives it nothing to measure. */
function open(){
  var doc=content.value,caret=content.selectionStart||0;
  overlay.style.display='block';
  document.body.style.overflow='hidden';
  if(!view){view=makeView(doc);bindKeys(view);}
  else{view.dispatch({changes:{from:0,to:view.state.doc.length,insert:doc}});}
  setCaret(caret);
  view.focus();
}

/* Read out of the view at the two moments that matter - on save and here -
   rather than syncing as you type. Focusing #content also re-points the
   palette's own 'last' at the real field, so a symbol clicked after closing
   lands somewhere visible. */
function close(){
  var text=view?view.state.doc.toString():content.value,
      caret=view?view.state.selection.main.head:0;
  content.value=text;
  overlay.style.display='none';
  document.body.style.overflow='';
  content.focus();
  var at=Math.max(0,Math.min(caret,content.value.length));
  content.selectionStart=content.selectionEnd=at;
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
  if(e.metaKey&&e.key==='9'){e.preventDefault();save();}
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
