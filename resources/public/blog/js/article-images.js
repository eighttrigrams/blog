(function(){
/* Pictures dropped or pasted into an article's content, on the edit page and in
   Zen (specs/article-images.md). GitHub's way, and tracker's - description_field,
   cm_textarea and image_prep there - in plain JS:

   1. a placeholder goes in where the picture was dropped, or at the caret for a
      paste: ![Uploading name…]()
   2. the picture is decoded the right way up and encoded again at full size,
      which is what takes its EXIF block (GPS position, camera, time) out. JPEG,
      PNG and WebP keep their format, AVIF and HEIC become JPEG, and a GIF goes
      as it is, so an animation keeps its frames;
   3. it goes to /article/<id>/image, and the placeholder becomes
      ![name](blog-images/<id>/file), or a line saying what went wrong. Never a
      silent drop.

   A save waits while a picture is still on its way up, since it would keep the
   placeholder for good: the form's submit (Save, Save new version, Publish, the
   chord) and Zen's cmd+9, which asks refuseSave(). */

var content=document.getElementById('content'),
    url=content&&content.getAttribute('data-image-upload');
if(!url)return;

var views=[],pending={},notice=null;

/* What a file is, by its type, or by its name where the type is blank (a HEIC
   on a browser that does not know it). No SVG: see et.blog.images. */
var KIND={'image/jpeg':'jpg','image/png':'png','image/webp':'webp','image/gif':'gif',
          'image/avif':'avif','image/heic':'heic','image/heif':'heic',
          jpg:'jpg',jpeg:'jpg',png:'png',webp:'webp',gif:'gif',avif:'avif',heic:'heic',heif:'heic'},
    ENCODE={jpg:'image/jpeg',png:'image/png',webp:'image/webp',avif:'image/jpeg',heic:'image/jpeg'},
    EXT={'image/jpeg':'jpg','image/png':'png','image/webp':'webp'};

function base(name){return (name||'').replace(/\.[A-Za-z0-9]{1,5}$/,'');}
function kind(f){
  var ext=/\.([A-Za-z0-9]+)$/.exec(f.name||'');
  return KIND[(f.type||'').toLowerCase()]||(ext&&KIND[ext[1].toLowerCase()])||null;
}
/* The name as it may stand inside ![…]: without what would end it or start a tag. */
function alt(name){
  return base(name).replace(/[\[\]()<>\\`*_!|]/g,' ').replace(/\s+/g,' ').trim()||'image';
}

/* ---- the picture, without its metadata -------------------------------- */

function decode(file){
  return Promise.resolve()
    .then(function(){return createImageBitmap(file,{imageOrientation:'from-image'});})
    .catch(function(){
      /* An <img> honours the EXIF orientation too, for a browser that will not
         take the option. */
      return new Promise(function(resolve,reject){
        var u=URL.createObjectURL(file),img=new Image();
        img.onload=function(){URL.revokeObjectURL(u);resolve(img);};
        img.onerror=function(){URL.revokeObjectURL(u);
          reject(new Error('this browser cannot read it.'));};
        img.src=u;
      });
    });
}

/* {blob, name} ready to upload. A canvas has no metadata to write, so encoding
   again is what strips it. */
function prepare(file,k){
  if(k==='gif')return Promise.resolve({blob:file,name:base(file.name)+'.gif'});
  var type=ENCODE[k];
  return decode(file).then(function(src){
    var w=src.naturalWidth||src.width,h=src.naturalHeight||src.height,
        canvas=document.createElement('canvas'),g;
    canvas.width=w;canvas.height=h;
    g=canvas.getContext('2d');
    /* JPEG has no transparency: what was see-through would turn black. */
    if(type==='image/jpeg'){g.fillStyle='#fff';g.fillRect(0,0,w,h);}
    g.drawImage(src,0,0);
    if(src.close)src.close();
    return new Promise(function(resolve){canvas.toBlob(resolve,type,0.92);})
      .then(function(blob){
        canvas.width=canvas.height=0;
        if(!blob)throw new Error('this browser could not encode it.');
        /* A browser that cannot write WebP answers with a PNG instead of
           refusing, so the name follows what came back. */
        return {blob:blob,name:base(file.name)+'.'+(EXT[blob.type]||'png')};
      });
  });
}

function upload(prepared){
  /* Longer than the 90-second deadline the server puts on its FTP session
     (images.clj session-ms), so that a hung upload ends in a line and the save
     guard lets go. Pictures queued behind one another can still pass it. A timed-out picture may still have
     arrived: an extra file, which the line says. */
  return fetch(url+'?filename='+encodeURIComponent(prepared.name),
               {method:'POST',body:prepared.blob,signal:AbortSignal.timeout(120000)})
    .then(function(r){
      /* A stale session is a redirect to /login, which fetch follows to a 200. */
      if(r.redirected)return {error:'Session expired. Reload and log in again.'};
      return r.json().catch(function(){
        return {error:'Server did not answer with JSON ('+r.status+').'};});
    })
    .then(function(a){
      if(!a||a.error||!a.path)throw new Error((a&&a.error)||'Upload failed.');
      return a.path;
    })
    .catch(function(e){
      if(e.name==='TimeoutError')throw new Error('no answer from the server within two '+
        'minutes. It may have arrived all the same.');
      throw e;
    });
}

/* ---- the text --------------------------------------------------------- */

function placeholder(name){
  var docs=views.map(function(v){return v.state.doc.toString();}).join('\n');
  for(var n=1;;n++){
    var p='![Uploading '+name+(n>1?' ('+n+')':'')+'…]()';
    if(docs.indexOf(p)<0&&!pending[p])return p;
  }
}

/* A picture stands on its own line. At `pos` (a drop) or over the selection,
   and the caret after it unless `keep` says to leave the selection alone. */
function insert(view,text,pos,keep){
  var doc=view.state.doc.toString(),sel=view.state.selection.main,
      from=pos==null?sel.from:pos,to=pos==null?sel.to:pos,
      ins=(from>0&&doc.charAt(from-1)!=='\n'?'\n':'')+text+(doc.charAt(to)!=='\n'?'\n':'');
  view.dispatch(keep?{changes:{from:from,to:to,insert:ins}}:
                {changes:{from:from,to:to,insert:ins},
                 selection:{anchor:from+ins.length},scrollIntoView:true});
}

/* In every view that has it: Zen's and the page's editor hand the text back
   and forth, so the one not on screen gets its copy right too. When the one on
   screen (Zen's while it is open, it is attached last) has lost the placeholder
   - undone, typed into - the line goes in at its caret: never a picture on the
   webspace that the text does not mention. */
function replace(ph,text){
  var shown=views.filter(function(v){return v.dom.getClientRects().length;}),
      live=shown[shown.length-1]||views[0];
  /* After the selection, or at the caret: never over what is selected, and the
     selection stays as it is. */
  if(live.state.doc.toString().indexOf(ph)<0)insert(live,text,live.state.selection.main.to,true);
  views.forEach(function(v){
    var i=v.state.doc.toString().indexOf(ph);
    if(i>=0)v.dispatch({changes:{from:i,to:i+ph.length,insert:text}});
  });
}

function take(view,files,pos,away){
  var lines=[],jobs=[];
  files.forEach(function(f){
    var k=kind(f);
    if(!k){lines.push('⚠ '+f.name+' not uploaded: only JPEG, PNG, GIF, WebP, AVIF '+
                      'and HEIC pictures are taken.');return;}
    var ph=placeholder(f.name);
    pending[ph]=true;
    lines.push(ph);
    jobs.push({file:f,kind:k,ph:ph});
  });
  if(!lines.length)return;
  insert(view,lines.join('\n'),pos);
  if(!away)view.focus();
  jobs.forEach(function(j){
    prepare(j.file,j.kind).then(upload)
      .then(function(path){replace(j.ph,'!['+alt(j.file.name)+']('+path+')');},
            function(e){replace(j.ph,'⚠ '+j.file.name+' not uploaded: '+e.message);})
      .then(function(){delete pending[j.ph];});
  });
}

/* ---- the editors ------------------------------------------------------ */

/* A clipboard can hold a picture and text at once. A screenshot is files only;
   a selection in Word or Google Docs is a picture of itself *and* plain text
   *and* HTML, and that one is text. So: files, unless both came with them.
   A picture by its type, or by its name where the type is blank (a HEIC). */
function pastedPictures(e){
  var cd=e.clipboardData;
  if(!cd)return null;
  var types=Array.prototype.slice.call(cd.types||[]),
      files=Array.prototype.filter.call(cd.files||[],function(f){
        return /^image\//.test(f.type||'')||kind(f);});
  if(!files.length||(types.indexOf('text/plain')>=0&&types.indexOf('text/html')>=0))return null;
  return files;
}

function draggingFiles(e){
  return !!(e.dataTransfer&&Array.prototype.indexOf.call(e.dataTransfer.types||[],'Files')>=0);
}

/* On the editor's outer element in the capture phase, ahead of CodeMirror's own
   handlers on its content: CodeMirror reads a dropped file as text and inserts
   it, which for a picture is a screenful of binary. So every file drop is taken
   from it, pictures or not; a text paste goes through as ever.

   Or on another element that stands for the editor's text: Zen's view mode,
   whose rendering takes pictures into the text underneath it. at(e) says where
   in the text a drop or a paste goes (null: over the selection), and the focus
   stays on that element, the editor being out of sight. */
function listen(view,el,at){
  var away=el!==view.dom;
  el.addEventListener('paste',function(e){
    var files=pastedPictures(e);
    if(!files)return;
    e.preventDefault();e.stopPropagation();
    take(view,files,at(e),away);
  },true);
  el.addEventListener('dragover',function(e){
    if(!draggingFiles(e))return;
    e.preventDefault();
    e.dataTransfer.dropEffect='copy';
  },true);
  el.addEventListener('drop',function(e){
    if(!draggingFiles(e))return;
    e.preventDefault();e.stopPropagation();
    take(view,Array.prototype.slice.call(e.dataTransfer.files),at(e),away);
  },true);
}

function attach(view){
  views.push(view);
  listen(view,view.dom,function(e){
    return e.type==='drop'?view.posAtCoords({x:e.clientX,y:e.clientY}):null;
  });
}

/* ---- saving waits ----------------------------------------------------- */

function refuseSave(){
  if(!Object.keys(pending).length)return false;
  if(!notice){
    notice=document.createElement('div');
    notice.setAttribute('role','alert');
    notice.style.cssText='position:fixed;top:10px;left:50%;transform:translateX(-50%);'+
      'z-index:10001;background:#fff;border:1px solid rgba(0,0,0,0.2);border-radius:4px;'+
      'padding:0.4rem 0.9rem;box-shadow:0 2px 8px rgba(0,0,0,0.1);';
  }
  notice.textContent='Not saved: wait for the picture to finish uploading.';
  document.body.appendChild(notice);
  clearTimeout(notice.timer);
  notice.timer=setTimeout(function(){notice.remove();},4000);
  return true;
}

content.form.addEventListener('submit',function(e){if(refuseSave())e.preventDefault();});

window.BlogArticleImages={attach:attach,listen:listen,refuseSave:refuseSave};
var inline=window.BlogEditors&&window.BlogEditors.get('content');
if(inline)attach(inline);
})();
