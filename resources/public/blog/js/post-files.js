(function(){
/* The files already on the webspace for this post, listed whether or not the
   post points at any of them. An image uploaded and then unlinked - a second
   attempt, a wrong crop - is otherwise invisible: nothing on the site links it
   and only an FTP client would ever find it again.

   Fetched rather than rendered with the page, because it costs an FTP round trip
   and opening an editor should not wait on one. */

var box=document.getElementById('post-files');
if(!box)return;

var postId=box.getAttribute('data-post-id'),
    field=document.getElementById('image');

function kb(size){
  if(typeof size!=='number'||size<0)return '';
  return size<1024?(size+' B'):(Math.round(size/1024)+' KB');
}

function render(files,unconfigured){
  box.textContent='';
  if(unconfigured){
    var none=document.createElement('span');
    none.className='muted';
    none.textContent='Uploads are not configured on this server.';
    box.appendChild(none);
    return;
  }
  if(!files.length){
    var empty=document.createElement('span');
    empty.className='muted';
    empty.textContent='No files uploaded for this post yet.';
    box.appendChild(empty);
    return;
  }
  var heading=document.createElement('div');
  heading.className='muted';
  heading.textContent='Files for this post ('+files.length+'):';
  box.appendChild(heading);

  var list=document.createElement('ul');
  list.style.cssText='list-style:none;padding:0;margin:0.3rem 0 0 0;';
  files.forEach(function(f){
    var li=document.createElement('li');
    li.style.cssText='display:flex;gap:0.5rem;align-items:center;margin-bottom:0.2rem;';

    /* The path, and whether the post is currently pointing at it. */
    var name=document.createElement('code');
    name.textContent=f.path;
    if(field&&field.value===f.path){
      var inUse=document.createElement('span');
      inUse.className='muted';
      inUse.textContent='(in use)';
      li.appendChild(name);
      li.appendChild(inUse);
    }else{
      li.appendChild(name);
      /* Not a link to the file: the point is to put it in the field. Nothing is
         saved by this - the ordinary Save writes it, so a wrong click is undone
         by not saving. */
      var use=document.createElement('button');
      use.type='button';
      use.className='btn btn-small';
      use.textContent='Use';
      use.addEventListener('click',function(){
        field.value=f.path;
        field.dispatchEvent(new Event('input',{bubbles:true}));
        field.dispatchEvent(new Event('change',{bubbles:true}));
        load();
      });
      li.appendChild(use);
    }

    var size=document.createElement('span');
    size.className='muted';
    size.textContent=kb(f.size);
    li.appendChild(size);
    list.appendChild(li);
  });
  box.appendChild(list);
}

function load(){
  box.textContent='';
  var loading=document.createElement('span');
  loading.className='muted';
  loading.textContent='Looking for files…';
  box.appendChild(loading);
  fetch('/post/'+postId+'/images')
    .then(function(r){
      if(r.redirected)return {files:[]};
      return r.json().catch(function(){return {files:[]};});
    })
    .then(function(answer){render(answer.files||[],answer.unconfigured);})
    .catch(function(){
      box.textContent='';
      var bad=document.createElement('span');
      bad.style.color='#dc3545';
      bad.textContent='Could not list files.';
      box.appendChild(bad);
    });
}

window.BlogPostFiles={reload:load};
load();
})();
