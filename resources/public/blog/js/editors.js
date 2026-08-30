(function(){
/* Every textarea marked data-editor becomes a CodeMirror with Daniel's IJKL
   bindings on it. The mounting itself is not blog's code - it is
   keyboard-wizardry/codemirror, through window.IJKL from the vendored bundle -
   and neither are the bindings. What is blog's is here: which fields get one
   (the marker in views.clj), and the symbol palette, which has to keep working.

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

/* ---- the save chord --------------------------------------------------- */

/* ⌘9 (and ⌘⏎ / ⌃⏎) saves the page you are editing, so the answer to "does ⌘9
   work here?" does not depend on which box you are standing in. Zen already had
   it; the plain edit pages did not, which is the gap this closes.

   `e.code` for the digit, not `e.key`: a modifier can change what a key *is* -
   ⌘9 does not necessarily arrive as "9" - while `Digit9` names the physical key
   whatever is held down with it. That is the rule claude-coordinator's
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

function targetForm(){
  var view=focused(),name=null;
  if(view){for(var n in views){if(views[n]===view){name=n;break;}}}
  var areas=document.querySelectorAll('textarea[data-editor]');
  for(var i=0;i<areas.length;i++){
    if(!name||areas[i].id===name||areas[i].name===name)return areas[i].form;
  }
  return areas.length?areas[0].form:null;
}

document.addEventListener('keydown',function(e){
  if(!saveChord(e))return;
  /* Zen has its own handler and its own save. Leave it alone when it is up.
     Same test zen.js makes: the overlay carries an inline display:none. */
  var zen=document.getElementById('zen-overlay');
  if(zen&&zen.style.display!=='none')return;
  var form=targetForm();
  if(!form)return;
  e.preventDefault();
  /* Which button the chord means, said out loud in the markup rather than
     inferred - the same way data-editor marks the fields that want the keymap.
     The article editor has three submits: Save, "Save new version" (name
     save-version, and behind a confirm()) and Publish (name publish). The chord
     means Save. Submitting with no submitter happens to do that today, because
     Save is the one button without a name, but that is an accident of the
     markup and not something to rely on. */
  var save=form.querySelector('[data-save-chord]');
  if(form.requestSubmit){form.requestSubmit(save||undefined);}
  else if(save){save.click();}
  else{var b=form.querySelector('button[type="submit"],input[type="submit"]');
       if(b)b.click();}
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
