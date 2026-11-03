/* Native IPC only: no network resource, eval, or untrusted HTML. */
const invoke=window.__TAURI__.core.invoke;
const $=id=>document.getElementById(id);
let lastUri='',working=false;
function text(tag,value){const e=document.createElement(tag);e.textContent=value;return e;}
async function call(name,args){try{return await invoke(name,args);}catch(e){$('error').textContent=String(e);throw e;}}
async function refresh(){if(working)return;working=true;try{
 const s=await call('snapshot');
 $('status').textContent=!s.enabled?'Receiver paused':s.connected?`Connected · ${s.connected}`:'Ready to pair';
 $('enabled').checked=s.enabled;$('permission').hidden=s.accessibility;
 $('address').textContent=`${s.host}:${s.port}`;
 $('latency').textContent=s.packets?`${s.dispatch_median_us} / ${s.dispatch_p95_us} μs · ${s.packets} events`:'Waiting for input';
 $('error').textContent=s.error||'';
 if(lastUri!==s.pairing_uri){lastUri=s.pairing_uri;$('uri').value=lastUri;$('code').textContent=s.pairing_code;
   const svg=await call('qr');const doc=new DOMParser().parseFromString(svg,'image/svg+xml');$('qr').replaceChildren(document.importNode(doc.documentElement,true));}
 $('devices').replaceChildren();if(!s.devices.length)$('devices').textContent='Your phone will feel right at home here. Pair it to get started.';
 for(const d of s.devices){const row=text('div','');row.className='device';row.append(text('strong',d.name),text('small',`Trusted · ${d.id.slice(0,8)}`));const b=text('button','Forget device');b.className='quiet';b.onclick=()=>call('revoke',{id:d.id});row.append(b);$('devices').append(row);}
 // Avoid replacing approval buttons under the pointer on each refresh.
 const key=JSON.stringify(s.pending);if($('pending').dataset.key!==key){$('pending').dataset.key=key;$('pending').replaceChildren();for(const p of s.pending){const row=text('div','');row.className='request';row.append(text('h2',p.name),text('p',`Check this code on your phone: ${p.code}`));for(const [label,allow] of [['Approve',true],['Deny',false]]){const b=text('button',label);b.onclick=()=>call('approve',{id:p.id,allow});row.append(b);}$('pending').append(row);}}
}catch{}finally{working=false;}}
$('rotate').onclick=()=>call('rotate');$('disconnect').onclick=()=>call('disconnect');$('enabled').onchange=e=>call('enabled',{value:e.target.checked});$('permissions').onclick=()=>call('permissions');
$('copy').onclick=async()=>{try{await navigator.clipboard.writeText(lastUri);$('copy').textContent='Copied';}catch{$('uri').select();}};
$('autostart').onchange=async e=>{try{await invoke(`plugin:autostart|${e.target.checked?'enable':'disable'}`);}catch(err){$('error').textContent=String(err);}};
invoke('plugin:autostart|is_enabled').then(v=>$('autostart').checked=v).catch(()=>{});
refresh();setInterval(refresh,750);
