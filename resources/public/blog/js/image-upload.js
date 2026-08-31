(function(){
/* The post editor's image upload. Sends the file to /post/:id/image, which puts
   it on the All-Inkl webspace under blog-images/posts/<today>/, and writes the
   path it answers with into the Image field.

   fetch and not a form post, deliberately: this control sits inside the edit
   form, and navigating away to upload would take whatever is unsaved in the
   Content editor with it. Nothing here saves the post - the path lands in the
   field and the ordinary Save (or the chord) writes it, so an upload you did not
   mean is undone by simply not saving. */

var box=document.getElementById('image-upload');
if(!box)return;

var file=document.getElementById('image-file'),
    button=document.getElementById('image-upload-btn'),
    status=document.getElementById('image-upload-status'),
    field=document.getElementById('image'),
    postId=box.getAttribute('data-post-id');

function say(message,bad){
  status.textContent=message;
  status.style.color=bad?'#dc3545':'rgba(0,0,0,0.5)';
}

function upload(){
  var chosen=file.files&&file.files[0];
  if(!chosen){say('Choose a file first.',true);return;}
  button.disabled=true;
  say('Uploading '+chosen.name+'…');
  /* The file as the raw body, its name in the query string. No multipart: one
     file needs no envelope, and ring-core 1.9.6's multipart middleware wants a
     servlet API this app does not carry. */
  fetch('/post/'+postId+'/image?filename='+encodeURIComponent(chosen.name),
        {method:'POST',body:chosen})
    .then(function(r){
      /* A stale session answers with a redirect to /login, which fetch follows to
         a perfectly good 200 - and that page is not JSON. Anything redirected is
         a failure however it ended up. */
      if(r.redirected)return {error:'Session expired. Reload and log in again.'};
      return r.json().catch(function(){return {error:'Server did not answer with JSON ('+r.status+').'};});
    })
    .then(function(answer){
      button.disabled=false;
      if(!answer||answer.error){say((answer&&answer.error)||'Upload failed.',true);return;}
      field.value=answer.path;
      /* The field is a plain input, but say so anyway: anything watching the
         form for changes - the divergence check in editors.js, for one - learns
         about a programmatic write only from an event. */
      field.dispatchEvent(new Event('input',{bubbles:true}));
      field.dispatchEvent(new Event('change',{bubbles:true}));
      say('Uploaded. Save to keep it.');
      file.value='';
      /* The file list below is now out of date by exactly one file. */
      if(window.BlogPostFiles)window.BlogPostFiles.reload();
    })
    .catch(function(){
      button.disabled=false;
      say('Upload failed.',true);
    });
}

button.addEventListener('click',upload);
})();
