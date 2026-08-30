(function(){
/* Every textarea marked data-editor becomes a CodeMirror with Daniel's IJKL
   bindings on it. The mounting itself is not blog's code - it is
   keyboard-wizardry/codemirror, through window.IJKL from the vendored bundle -
   and neither are the bindings. What is blog's is here: which fields get one
   (the marker in views.clj), the save and escape behaviour the plurama UX
   philosophy describes, and the symbol palette, which has to keep working.

   Marked, not "every textarea on the page", on purpose: the message box on
   /email and the comment and reply forms belong to visitors, who did not ask for
   somebody else's keymap and should not pay 270KB for it either. */

if(!window.IJKL||!window.CM6)return;
if(!document.querySelector('textarea[data-editor]'))return;

var views=window.IJKL.fromTextareas(document,window.CM6);

/* Which of them the caret is in, or null. CodeMirror answers this itself. */
function focused(){
  for(var name in views){if(views[name].hasFocus)return views[name];}
  return null;
}

window.BlogEditors={
  views:views,
  get:function(name){return views[name]||null;},
  focused:focused
};

/* Zen is exempt from everything below. zen.js says in as many words that it has
   no Escape handler on purpose - a stray Escape while writing must not throw you
   out - and it has its own save. This is the test it makes on itself. */
function zenOpen(){
  var zen=document.getElementById('zen-overlay');
  return !!(zen&&zen.style.display!=='none');
}

function targetForm(){
  var view=focused(),name=null;
  if(view){for(var n in views){if(views[n]===view){name=n;break;}}}
  var areas=document.querySelectorAll('textarea[data-editor]');
  for(var i=0;i<areas.length;i++){
    if(!name||areas[i].id===name||areas[i].name===name)return areas[i].form;
  }
  return areas.length?areas[0].form:null;
}

/* ---- the save chord --------------------------------------------------- */

/* Cmd+9 (and Cmd/Ctrl+Enter) saves the page you are editing, so the answer to
   "does the chord work here?" does not depend on which box you are standing in.
   Zen already had it; the plain edit pages did not, which is the gap this closes.

   `e.code` for the digit, not `e.key`: a modifier can change what a key *is* -
   Cmd+9 does not necessarily arrive as "9" - while `Digit9` names the physical
   key whatever is held down with it. That is the rule claude-coordinator's
   ui/keys.cljs already writes down, and this follows it. `e.key` is still
   accepted so nothing that worked before stops working.

   requestSubmit() and NOT submit(): the vendored CodeMirror copies the document
   back into the textarea on the form's "submit" event, and form.submit() does
   not fire that event. Submitting the wrong way would post whatever the textarea
   held before the editor mounted - a silent save of stale text. */
function saveChord(e){
  return ((e.metaKey||e.ctrlKey)&&e.key==='Enter')||
         (e.metaKey&&(e.code==='Digit9'||e.key==='9'));
}

function submitSave(form){
  /* Which button the chord means, said out loud in the markup rather than
     inferred - the same way data-editor marks the fields that want the keymap.
     The article editor has three submits: Save, "Save new version" (name
     save-version, and behind a confirm()) and Publish (name publish). The chord
     means Save. */
  var save=form.querySelector('[data-save-chord]');
  if(form.requestSubmit){form.requestSubmit(save||undefined);}
  else if(save){save.click();}
  else{var b=form.querySelector('button[type="submit"],input[type="submit"]');
       if(b)b.click();}
}

document.addEventListener('keydown',function(e){
  if(!saveChord(e))return;
  if(zenOpen())return;
  var form=targetForm();
  if(!form)return;
  e.preventDefault();
  submitSave(form);
});

/* ---- escaping, and what a divergence costs ---------------------------- */

/* Escape means get back to the previous page. If what is on screen still matches
   what was saved, that is free and immediate. If it does not, the divergence has
   to be answered for first: discard on the left, keep editing on the right, and
   keep editing is the default - so a reflexive Enter never throws work away. */

function fieldValue(el){
  if(el.matches('textarea[data-editor]')){
    var v=views[el.id||el.name];
    if(v)return v.state.doc.toString();
  }
  if(el.type==='checkbox'||el.type==='radio')return el.checked?'1':'';
  return el.value;
}

/* A form's state as one string. Reads data-editor fields from CodeMirror rather
   than from the textarea: the bundle only copies the document back on submit, so
   asking the textarea would make every edit look like no edit at all. */
function snapshot(form){
  var out=[],els=form.querySelectorAll('input[name],textarea[name],select[name]');
  for(var i=0;i<els.length;i++){
    out.push(els[i].name+String.fromCharCode(31)+fieldValue(els[i]));
  }
  return out.join(String.fromCharCode(30));
}

var escapeForm=targetForm(),
    baseline=escapeForm?snapshot(escapeForm):null;

function diverged(){
  return !!(escapeForm&&baseline!==null&&snapshot(escapeForm)!==baseline);
}

function leave(){
  if(history.length>1)history.back();
}

var modal=null;

function closeModal(){
  if(!modal)return;
  if(modal.overlay.parentNode)modal.overlay.parentNode.removeChild(modal.overlay);
  modal=null;
}

function openModal(){
  if(modal)return;
  var overlay=document.createElement('div');
  overlay.setAttribute('role','dialog');
  overlay.setAttribute('aria-modal','true');
  overlay.style.cssText='position:fixed;top:0;right:0;bottom:0;left:0;z-index:100;'+
    'background:rgba(0,0,0,0.25);display:flex;align-items:center;justify-content:center;';
  var box=document.createElement('div');
  box.style.cssText='background:#fff;border:1px solid rgba(0,0,0,0.1);border-radius:5px;'+
    'padding:1.5rem;max-width:26rem;box-shadow:0 2px 12px rgba(0,0,0,0.15);';
  var text=document.createElement('p');
  text.textContent='This has unsaved changes.';
  text.style.cssText='margin:0 0 1rem 0;';
  var actions=document.createElement('div');
  actions.style.cssText='display:flex;gap:0.75rem;';

  /* Left is discard, right is keep editing - the order the philosophy states,
     which also puts the safe one under the resting hand. */
  var discard=document.createElement('button');
  discard.type='button';
  discard.className='btn btn-danger';
  discard.textContent='Discard';
  var keep=document.createElement('button');
  keep.type='button';
  /* danger + cancel is the pairing the site's own confirm boxes already use, so
     the destructive choice reads as the destructive one. Which is the default is
     said by the focus ring, not by the colour. */
  keep.className='btn btn-cancel';
  keep.textContent='Keep editing';

  discard.addEventListener('click',function(){closeModal();leave();});
  keep.addEventListener('click',function(){closeModal();});

  actions.appendChild(discard);
  actions.appendChild(keep);
  box.appendChild(text);
  box.appendChild(actions);
  overlay.appendChild(box);
  document.body.appendChild(overlay);

  modal={overlay:overlay,buttons:[discard,keep]};
  keep.focus();                    /* the default, so a bare Enter keeps editing */
}

function cycle(step){
  if(!modal)return;
  var i=modal.buttons.indexOf(document.activeElement);
  if(i===-1)i=modal.buttons.length-1;
  modal.buttons[(i+step+modal.buttons.length)%modal.buttons.length].focus();
}

document.addEventListener('keydown',function(e){
  if(modal){
    /* Cmd+j leftwards, Cmd+l rightwards. Enter needs no handling: the browser
       already activates a focused button. */
    if(e.metaKey&&(e.code==='KeyJ'||e.key==='j')){e.preventDefault();cycle(-1);return;}
    if(e.metaKey&&(e.code==='KeyL'||e.key==='l')){e.preventDefault();cycle(1);return;}
    /* Escape out of the modal is the default answer, not the destructive one. */
    if(e.key==='Escape'){e.preventDefault();closeModal();}
    return;
  }
  if(e.key!=='Escape')return;
  if(e.metaKey||e.ctrlKey||e.altKey||e.shiftKey)return;
  if(zenOpen())return;
  if(!escapeForm)return;
  e.preventDefault();
  if(diverged())openModal();else leave();
});

/* ---- the symbol palette ---------------------------------------------- */

/* Daniel's palette script inserts at a textarea's selectionStart, and these
   textareas are never focused any more - the editor is. So intercept in the
   capture phase on the parent, which runs before his target-phase listener on
   the button, and stopPropagation so his never fires and his script stays
   untouched. Exactly what zen.js already does for the Zen overlay.

   When no editor has the caret - a plain textarea on the same page - this does
   nothing and lets his script have the click. */
var palette=document.querySelector('.symbol-palette');
if(palette){
  palette.addEventListener('click',function(e){
    var view=focused();
    if(!view)return;
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
