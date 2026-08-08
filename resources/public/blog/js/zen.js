(function(){
var openBtn=document.getElementById('zen-open');
if(!openBtn)return;
var overlay=document.getElementById('zen-overlay'),
    zen=document.getElementById('zen-content'),
    content=document.getElementById('content'),
    form=content.form,
    flash=document.getElementById('save-flash');
function carry(from,to){to.value=from.value;to.selectionStart=from.selectionStart;to.selectionEnd=from.selectionEnd;}
function isOpen(){return overlay.style.display!=='none';}
function open(){carry(content,zen);overlay.style.display='block';document.body.style.overflow='hidden';zen.focus();}
/* The palette writes .value directly and fires no input event, so zen is read
   at the two moments that matter - on save and here - rather than synced while
   typing. Focusing #content also re-points the palette's own 'last' at the
   real field, so a symbol clicked after closing lands somewhere visible. */
function close(){carry(zen,content);overlay.style.display='none';document.body.style.overflow='';content.focus();}
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
  fd.set('content',zen.value);
  fd.set('no-redirect','1');
  /* Submitting a form normalizes textarea newlines to CRLF, FormData does not.
     Without doing it by hand an in-between save would rewrite the line endings
     of every multiline field, and the next version bump would see a change
     nobody made and act on it. (Keep the button labels out of this comment:
     one edit-page test greps the body for them.) */
  var body=new URLSearchParams();
  fd.forEach(function(v,k){body.append(k,String(v).replace(/\r\n|\r|\n/g,'\r\n'));});
  fetch(form.action,{method:'POST',body:body})
    .then(function(r){mark(r.status===204);})
    .catch(function(){mark(false);});}
openBtn.addEventListener('click',open);
document.getElementById('zen-close').addEventListener('click',close);
/* No Escape handler on purpose: the X is the only way out, and a stray Escape
   while writing must not throw you out. */
document.addEventListener('keydown',function(e){
  if(!isOpen())return;
  if(e.metaKey&&e.key==='9'){e.preventDefault();save();}
});
})();
