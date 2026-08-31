(function(){
/* The sections are <details> and start closed, which breaks the one thing that
   depended on them being open: the handlers redirect to /dashboard#subscribers
   and friends after doing their work. A browser scrolls to a closed <details>
   and leaves it closed, so the answer to "did that work?" would be a heading and
   nothing under it.

   Open the one the fragment names, and do the same when the fragment changes,
   since clicking an in-page link fires no navigation. */
function openFromHash(){
  var id=(location.hash||'').replace('#','');
  if(!id)return;
  var el=document.getElementById(id);
  if(el&&el.tagName==='DETAILS'){
    el.open=true;
    el.scrollIntoView();
  }
}
openFromHash();
window.addEventListener('hashchange',openFromHash);
})();
