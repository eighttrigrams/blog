(function(){
/* Editing a Note where it stands. Click a Note's text and that Note becomes an
   editor; click anywhere outside it and the edit is saved and the Note is read
   again; click another Note and the first one is saved before the second opens.
   There is no Edit button on this page any more - this is what replaced it.

   One editor at a time, on purpose: the box is a list of short texts, and the
   rule "the thing you clicked away from is saved" only stays simple while there
   is exactly one thing to have clicked away from. */

if(!window.IJKL||!window.CM6)return;

var flash=document.getElementById('save-flash'),
    active=null,   /* the Note being edited, or null */
    mounted={};    /* note id -> its CodeMirror view, made on the first edit */

/* The same mark the edit page's in-between save uses, and the same reason for
   building a fresh node: a second save must restart the animation rather than
   be swallowed by the one still running. */
function mark(ok){
  if(!flash)return;
  while(flash.firstChild)flash.removeChild(flash.firstChild);
  var s=document.createElement('span');
  s.className='save-flash-mark'+(ok?'':' failed');
  s.textContent=ok?'✓':'✗';
  flash.appendChild(s);
}

/* ---- one Note -------------------------------------------------------- */

function partsOf(item){
  return {id:item.getAttribute('data-note-id'),
          item:item,
          text:item.querySelector('.note-text'),
          host:item.querySelector('.note-editor'),
          area:item.querySelector('.note-editor textarea')};
}

/* The bundle takes an editor's whole shape off the textarea it replaces - font,
   padding, border, and the height, all read at mount - which is why one of these
   looks like the Add Note box above it. The height it would read is a form
   field's, so set it first: a Note opens at the height of what it says, and
   clicking one does not shove the rest of the box down the page.

   From there it is the drag handle's job (see the CSS): the editor does not grow
   as you write, exactly like every other textarea here. */
function fit(area){
  /* Flattened first, because scrollHeight never reports less than the box it is
     read from: measured at its natural height a one-line Note would come back
     two rows tall, which is the default size of an empty textarea. */
  area.style.height='0';
  /* box-sizing is border-box, and scrollHeight is content plus padding without
     the border - so without the two pixels a line that fits would scroll. */
  area.style.height=(area.scrollHeight+2)+'px';
}

/* Mounted once and kept: re-mounting would mean unpicking what fromTextarea did
   to the textarea, and keeping it means a Note dragged taller stays that way
   while the page is open. */
function editorFor(p){
  if(!mounted[p.id]){
    fit(p.area);
    mounted[p.id]=window.IJKL.fromTextarea(p.area,window.CM6);
  }
  return mounted[p.id];
}

function open(item){
  var p=partsOf(item);
  if(!p.host||!p.area)return;
  /* Reveal before mounting: CodeMirror measures what it is put into, and a
     display:none box measures as nothing. Same task, so nothing is painted in
     between and the swap has no flicker. */
  p.text.style.display='none';
  p.host.style.display='';
  var view=editorFor(p),
      /* A click on the *rendered* Note cannot say where in the source it
         landed, so the caret goes to the end - which is where a Note is
         usually carried on anyway. */
      at=view.state.doc.length;
  view.dispatch({selection:{anchor:at,head:at}});
  view.focus();
  active={p:p,view:view,was:view.state.doc.toString()};
}

function read(a){
  a.p.host.style.display='none';
  a.p.text.style.display='';
}

/* Save and go back to reading. Resolves true when the Note is closed, false
   when it is not: a save that did not go through keeps the editor open with the
   text still in it, because there is nowhere else that text exists. */
function close(){
  var a=active;
  if(!a)return Promise.resolve(true);
  /* A second click while the save is still in flight waits on that save rather
     than starting another one - two POSTs of the same edit is a write too many. */
  if(a.closing)return a.closing;
  var text=a.view.state.doc.toString();
  /* Looked at, not changed: no write at all. That keeps `modified_at` honest,
     and leaves the stored line endings exactly as they are rather than
     rewriting them (see save()). */
  if(text===a.was){active=null;read(a);return Promise.resolve(true);}
  a.closing=save(a,text).then(function(html){
    a.closing=null;
    if(html===null){mark(false);return false;}
    a.p.text.innerHTML=html;
    highlight(a.p.text);
    active=null;
    read(a);
    mark(true);
    return true;
  });
  return a.closing;
}

/* The POST the Save button on /notes/:id/edit makes, minus the navigation.
   Resolves to the Note rendered, or null when it did not save. */
function save(a,text){
  var body=new URLSearchParams();
  /* Submitting a form normalizes a textarea's newlines to CRLF; neither
     URLSearchParams nor doc.toString() does. Left alone, this save and that
     button would write the same Note as different bytes. */
  body.append('text',text.replace(/\r\n|\r|\n/g,'\r\n'));
  body.append('no-redirect','1');
  /* keepalive: clicking a link, or Delete, saves the open Note on the way out,
     and the navigation that follows would otherwise cancel the request. */
  return fetch('/notes/'+a.p.id,{method:'POST',body:body,keepalive:true})
    .then(function(r){
      /* A stale session answers with a redirect to /login, which fetch follows
         to a perfectly good 200 - and that page must never be pasted in where a
         Note's text was. Anything but a 200 arrived at directly is a failure. */
      if(r.status!==200||r.redirected)return null;
      return r.text();
    })
    .catch(function(){return null;});
}

/* A reload highlights the box's code blocks on the way in; a Note put back in
   place has to ask for its own. */
function highlight(el){
  if(!window.hljs)return;
  el.querySelectorAll('pre code').forEach(function(block){
    window.hljs.highlightElement(block);
  });
}

/* ---- the click ------------------------------------------------------- */

function closest(node,selector){
  return node&&node.closest?node.closest(selector):null;
}

/* The quick-edit variant of the philosophy: a Note is low stakes and has no
   page of its own to get back to, so Escape and the save chord mean the same
   thing here - save and blur. No divergence modal, because there is no
   divergence to answer for: close() writes what changed and returns to reading,
   and a Note that was only looked at is not written at all. */
document.addEventListener('keydown',function(e){
  if(!active)return;
  var quit=e.key==='Escape'||
           (e.metaKey&&(e.code==='Digit9'||e.key==='9'))||
           ((e.metaKey||e.ctrlKey)&&e.key==='Enter');
  if(!quit)return;
  e.preventDefault();
  close();
});

document.addEventListener('click',function(e){
  /* Inside the open editor is just writing. */
  if(active&&active.p.host.contains(e.target))return;
  var text=closest(e.target,'.note-text'),
      /* Which Note was clicked, if the click was on a Note's text at all. The
         heading row is not a way in: Delete lives there, and a click meant for
         it must not open an editor on its way. */
      item=text?closest(text,'.note-item'):null;
  if(!active){if(item)open(item);return;}
  close().then(function(closed){
    /* `!active` because clicks that arrived while the save was in flight share
       its one promise, and the first of them has already opened something. */
    if(closed&&item&&!active)open(item);
  });
});
})();
