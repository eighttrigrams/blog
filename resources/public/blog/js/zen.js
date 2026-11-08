(function(){
var openBtn=document.getElementById('zen-open');
if(!openBtn)return;
var overlay=document.getElementById('zen-overlay'),
    mount=document.getElementById('zen-content'),
    content=document.getElementById('content'),
    form=content.form,
    flash=document.getElementById('save-flash'),
    palette=document.querySelector('.symbol-palette'),
    sheet=document.getElementById('zen-view'),
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
    CM.keymap.of(c.defaultKeymap),
    /* A picture arriving while you look at the rendering: draw it again. */
    CM.EditorView.updateListener.of(function(u){if(u.docChanged&&viewing&&isOpen())draw();})
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
  /* Zen opens in edit mode, whichever mode it was left in. */
  viewing=false;
  overlay.classList.remove('viewing');
  if(!view){view=makeView(doc);bindKeys(view);
    if(window.BlogArticleImages){
      window.BlogArticleImages.attach(view);
      /* A drop on the rendering lands where a click there would; a paste where
         the caret last was in the text. */
      window.BlogArticleImages.listen(view,sheet,function(e){
        var at=e.type==='drop'?pointAt(e.clientX,e.clientY):null;
        return at==null?view.state.selection.main.head:at;
      });
    }}
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

/* ---- view mode -------------------------------------------------------- */

/* Escape shows the text rendered as the article page renders it, and a click on
   the rendering goes back into the text where it pointed (specs/zen-view-mode.md).
   zoo's markdown pane is the model: the editor stays mounted underneath, only
   out of sight, so the caret and the undo history survive a look at the
   rendering. */

var viewing=false,
    shown='',   /* the text the sheet was drawn from: its data-pos count in it */
    drawn=0;    /* the last draw asked for; an older answer arriving late is dropped */

function footnotes(){
  var f=window.BlogEditors?window.BlogEditors.get('footnotes'):null;
  return f?f.state.doc.toString():(form.elements.footnotes?form.elements.footnotes.value:'');
}

/* By the server, with et.blog.render, so that there is one renderer and the view
   shows what the article page will: footnotes from the form's footnotes field,
   citations quoted, pictures from the webspace. Nothing is saved. */
function draw(then){
  var n=++drawn,text=view.state.doc.toString();
  fetch(form.action+'/render',{method:'POST',
        body:new URLSearchParams({content:text,footnotes:footnotes()})})
    .then(function(r){
      /* A stale session is a redirect to /login, which fetch follows to a 200. */
      if(r.redirected)throw new Error('Session expired. Reload and log in again.');
      if(!r.ok)throw new Error('The server could not render it ('+r.status+').');
      return r.text();
    })
    .then(function(html){
      if(n!==drawn)return;
      sheet.innerHTML=html;
      shown=text;
      uploading();
      if(then)then();
    })
    .catch(function(e){
      if(n!==drawn)return;
      sheet.textContent='⚠ '+e.message;
      shown='';
    });
}

/* A picture on its way up is ![Uploading name…]() in the text, which renders as
   a picture with no source. It shows as what it is: a spinner and the words. */
function uploading(){
  sheet.querySelectorAll('img[src=""]').forEach(function(img){
    var alt=img.getAttribute('alt')||'';
    if(!/^Uploading .*…$/.test(alt))return;
    var s=document.createElement('span');
    s.className='zen-uploading';
    if(img.hasAttribute('data-pos'))s.setAttribute('data-pos',img.getAttribute('data-pos'));
    s.appendChild(document.createElement('i'));
    s.appendChild(document.createTextNode(alt));
    img.parentNode.replaceChild(s,img);
  });
}

function range(el){
  var m=/^(\d+)-(\d+)$/.exec(el.getAttribute('data-pos')||'');
  return m?[+m[1],+m[2]]:null;
}

/* The block a place in the text is in, or failing that the next one, or the one
   before: a blank line has no rendering of its own. */
var BLOCKS='p,h1,h2,h3,h4,h5,h6,li,pre,hr';
function blockAt(at){
  var inside=null,after=null,before=null;
  sheet.querySelectorAll('[data-pos]').forEach(function(el){
    if(!el.matches(BLOCKS))return;
    var r=range(el);
    if(r[0]<=at&&at<=r[1])inside=el;
    else if(r[0]>at){if(!after)after=el;}
    else before=el;
  });
  return inside||after||before;
}

/* The line you left rings once, in the middle of the sheet (zoo's pulse!). */
function ring(at){
  var el=blockAt(at);
  if(!el)return;
  var s=sheet.getBoundingClientRect(),r=el.getBoundingClientRect();
  sheet.scrollTop+=r.top-s.top-(sheet.clientHeight-r.height)/2;
  el.classList.add('zen-ring');
  el.addEventListener('animationend',function(){el.classList.remove('zen-ring');},{once:true});
}

function toView(){
  var at=view.state.selection.main.head;
  viewing=true;
  sheet.innerHTML='';
  shown='';
  overlay.classList.add('viewing');
  /* The keys and a paste come here now, not into a text nobody sees. */
  sheet.focus({preventScroll:true});
  draw(function(){ring(at);});
}

/* The caret says where it landed, once. A mark of blog's own beside CodeMirror's
   content rather than a class on its nodes, which CodeMirror redraws (tracker's
   landing field in et.tr.ui.codemirror says why that does not last). Two frames
   on, when the scroll to the middle has been made. */
function pop(){
  requestAnimationFrame(function(){requestAnimationFrame(function(){
    var c=view.coordsAtPos(view.state.selection.main.head);
    if(!c)return;
    var s=view.scrollDOM,b=s.getBoundingClientRect(),m=document.createElement('div');
    m.className='zen-pop';
    m.style.left=(c.left-b.left+s.scrollLeft)+'px';
    m.style.top=(c.top-b.top+s.scrollTop)+'px';
    m.style.height=(c.bottom-c.top)+'px';
    s.appendChild(m);
    m.addEventListener('animationend',function(){m.remove();});
  });});
}

function toEdit(at){
  viewing=false;
  overlay.classList.remove('viewing');
  if(at!=null){
    at=Math.min(at,view.state.doc.length);
    view.dispatch({selection:{anchor:at},
                   effects:window.CM6.EditorView.scrollIntoView(at,{y:'center'})});
  }
  view.focus();
  pop();
}

/* ---- a point on the rendering, as a place in the text ------------------ */

/* zoo's md-point (et.zoo.ui.md-point), by way of tracker's port, in plain JS.

   *Which* part of the text is told, not worked out: the nearest element that
   says data-pos="start-end", flexmark's own count. *Where* in it is aligned, not
   parsed. A rendering is its source with the markers taken out, so the rendered
   characters are the source's in order with some missing; walk both from the
   left and the source pointer lands where the point was. The marked elements
   inside (a link, a bold word) are fixed points of the walk: between two of
   them only what lies between them in the source is aligned against, so a full
   stop after a link cannot be matched to the one in its URL. */

/* What the typographic extension makes of what was typed. */
var TYPED={'\u201C':'"','\u201D':'"','\u2018':"'",'\u2019':"'",
           '\u2013':'-','\u2014':'-','\u2026':'.'};

/* Which index of src the rendered character at `offset` came from. zoo's walk,
   with tracker's addition (a rendered character that is nowhere in the rest of
   the source is the renderer's own, and passed over) and blog's: a curly quote,
   a dash or an ellipsis may stand for the straight one it was typed as. */
function column(src,rendered,offset){
  var si=0,ri=0;
  while(si<src.length&&ri<rendered.length){
    var r=rendered.charAt(ri),t=TYPED[r];
    if(src.charAt(si)!==r&&src.charAt(si)!==t){
      if(src.indexOf(r,si)<0&&(!t||src.indexOf(t,si)<0)){
        if(ri>=offset)return si;
        ri++;
      }else si++;
      continue;
    }
    if(ri>=offset)return si;
    si++;ri++;
  }
  return si;
}

/* The place in the text of `offset` in the text node `node` inside `el`, the
   nearest element with a data-pos. Whitespace-only text between tags is layout,
   not the author's. A footnote's number is not in the text: it is aligned as the
   FOOTNOTE: that made it, and a point on it lands there. */
function locate(el,node,offset){
  var r=range(el),seg={from:r[0],to:r[1],text:''},hit=null;
  (function walk(n){
    for(var c=n.firstChild;c;c=c.nextSibling){
      if(c.nodeType===3){
        var kept=c.nodeValue.trim()!==''||!!c.parentElement.closest('pre');
        if(c===node)hit={seg:seg,at:seg.text.length+(kept?offset:0)};
        if(kept)seg.text+=c.nodeValue;
      }else if(c.nodeType!==1){
      }else if(c.tagName==='SUP'){
        if(c.contains(node))hit={seg:seg,at:seg.text.length};
        seg.text+='FOOTNOTE:';
      }else if(range(c)){
        var cr=range(c);
        seg.to=Math.max(seg.from,cr[0]);
        seg={from:cr[1],to:r[1],text:''};
      }else walk(c);
    }
  })(el);
  if(!hit)return r[0];
  return hit.seg.from+column(shown.slice(hit.seg.from,hit.seg.to),hit.seg.text,hit.at);
}

function caretAt(x,y){
  if(document.caretPositionFromPoint){
    var p=document.caretPositionFromPoint(x,y);
    return p&&[p.offsetNode,p.offset];
  }
  if(document.caretRangeFromPoint){
    var q=document.caretRangeFromPoint(x,y);
    return q&&[q.startContainer,q.startOffset];
  }
  return null;
}

/* Where in the text the point (x,y) on the sheet points, or null if the sheet
   has nothing to point at. Off the text (a picture, the margin, below the end):
   the marked element under it, or else the block nearest it, vertical distance
   first (zoo's nearest), at the end the point is nearer to. */
function pointAt(x,y){
  var c=caretAt(x,y),node=c&&c[0],el=null;
  if(node&&node.nodeType===3&&sheet.contains(node)){
    el=node.parentElement.closest('[data-pos]');
    if(el)return locate(el,node,c[1]);
  }
  var under=document.elementFromPoint(x,y);
  el=under&&sheet.contains(under)?under.closest('[data-pos]'):null;
  if(!el){
    var best=null;
    sheet.querySelectorAll('[data-pos]').forEach(function(e){
      var b=e.getBoundingClientRect(),
          d=y<b.top?b.top-y:y>b.bottom?y-b.bottom:0;
      if(!best||d<best.d)best={el:e,d:d};
    });
    if(!best)return null;
    el=best.el;
  }
  var b=el.getBoundingClientRect(),r=range(el),end=r[1];
  while(end>r[0]&&/\s/.test(shown.charAt(end-1)))end--;
  return y<b.top||(y<=b.bottom&&x<b.left+b.width/2)?r[0]:end;
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
function save(){
  /* A picture still on its way up would be saved as its placeholder for good. */
  if(window.BlogArticleImages&&window.BlogArticleImages.refuseSave())return;
  var fd=new FormData(form);
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

/* The X is the only way out, and a stray Escape while writing must not throw
   you out: Escape shows the rendering, and in view mode it does nothing. */
document.addEventListener('keydown',function(e){
  if(!isOpen())return;
  if(e.key==='Escape'&&!viewing&&view&&!e.isComposing){e.preventDefault();toView();return;}
  /* `e.code` for the digit: a modifier can change what a key *is*, so â9 does
     not necessarily arrive as "9", while Digit9 names the physical key whatever
     is held down with it - the rule claude-coordinator's ui/keys.cljs states.
     `e.key` stays accepted so nothing that worked before stops working. */
  if((e.metaKey&&(e.code==='Digit9'||e.key==='9'))||
     ((e.metaKey||e.ctrlKey)&&e.key==='Enter')){e.preventDefault();save();}
});

/* A click on the rendering goes into the text, there. Not a click that ends a
   selection, nor a drag: those were a read. A link is a word like any other;
   with ⌘ held it is a link, and opens in a new tab. */
var down=null;
sheet.addEventListener('mousedown',function(e){down=[e.clientX,e.clientY];});
sheet.addEventListener('click',function(e){
  if(!viewing||e.button!==0)return;
  var a=e.target.closest('a[href]'),sel=window.getSelection(),
      moved=down&&Math.abs(e.clientX-down[0])+Math.abs(e.clientY-down[1])>4;
  if(a)e.preventDefault();
  if(a&&(e.metaKey||e.ctrlKey)){window.open(a.href,'_blank','noopener');return;}
  if(moved||(sel&&!sel.isCollapsed))return;
  toEdit(pointAt(e.clientX,e.clientY));
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
