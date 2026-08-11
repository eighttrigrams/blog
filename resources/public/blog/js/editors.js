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
