import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {createInterface} from 'node:readline';
import tls from 'node:tls';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const exe=process.argv[2]||`desktop/core/target/debug/examples/smoke_receiver${process.platform==='win32'?'.exe':''}`;
const child=spawn(exe,[],{stdio:['pipe','pipe','inherit']});
const lines=createInterface({input:child.stdout});
const timer=setTimeout(()=>{child.kill();throw Error('Smoke test timed out')},25000);
const packet=(kind,seq,a=0,b=0)=>{const p=Buffer.alloc(32);p.write('TP');p[2]=1;p[3]=kind;p.writeBigUInt64LE(BigInt(seq),4);p.writeBigUInt64LE(process.hrtime.bigint()/1000n,12);p.writeFloatLE(a,20);p.writeFloatLE(b,24);return p};
try{
 const [line]=await once(lines,'line');const {snapshot:s,certificate}=JSON.parse(line);const uri=new URL(s.pairing_uri);
 const der=Buffer.from(certificate,'hex');const ca=`-----BEGIN CERTIFICATE-----\n${der.toString('base64').match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----`;
 const id='00112233445566778899aabbccddeeff';
 async function dial(auth){
  const sock=tls.connect({host:s.host,port:s.port,servername:'thumbpad.local',ca,checkServerIdentity:(host,cert)=>{
   const err=tls.checkServerIdentity(host,cert);if(err)return err;
   if(crypto.createHash('sha256').update(cert.raw).digest('hex')!==s.fingerprint)return Error('pin mismatch');
  }});sock.on('error',()=>{});await once(sock,'secureConnect');
  let buffered=Buffer.alloc(0);let ended=false;const waiting=[];
  sock.on('data',b=>{buffered=Buffer.concat([buffered,b]);for(const f of waiting.splice(0))f()});sock.on('close',()=>{ended=true;for(const f of waiting.splice(0))f()});
  async function read(n){while(buffered.length<n){if(ended)throw Error('closed');await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('read timeout')),5000);waiting.push(()=>{clearTimeout(timer);resolve()})})}const b=buffered.subarray(0,n);buffered=buffered.subarray(n);return b}
  const body=Buffer.from(JSON.stringify({version:1,id,name:'Automated loopback phone',...auth}));const len=Buffer.alloc(2);len.writeUInt16LE(body.length);sock.write(Buffer.concat([len,body]));
  return {sock,read,hello:async()=>JSON.parse((await read((await read(2)).readUInt16LE())).toString()),closed:()=>ended};
 }
 // Give listener startup a moment; no browser, no input injection, no certificate bypass.
 await new Promise(r=>setTimeout(r,150));
 const bad=await dial({secret:'0'.repeat(64)});await assert.rejects(bad.hello());bad.sock.destroy();
 const good=await dial({secret:uri.searchParams.get('secret')});const hello=await good.hello();assert.equal(hello.ok,true);assert.equal(hello.token.length,64);
 const busy=await dial({token:hello.token});await assert.rejects(busy.hello());busy.sock.destroy();
 good.sock.write(packet(2,1,0,1));good.sock.write(packet(1,2,2,-1));good.sock.write(packet(2,3,0,0));
 const times=[];
 for(let i=0;i<100;i++){const before=performance.now();good.sock.write(packet(5,i+4));const p=await good.read(32);assert.equal(p[3],5);assert.equal(Number(p.readBigUInt64LE(4)),i+4);times.push(performance.now()-before)}
 good.sock.write(packet(6,104));good.sock.destroy();await new Promise(r=>setTimeout(r,100));
 const reconnect=await dial({token:hello.token});assert.equal((await reconnect.hello()).ok,true);
 reconnect.sock.write(packet(5,1));await reconnect.read(32);reconnect.sock.write(packet(5,1));await assert.rejects(reconnect.read(32));reconnect.sock.destroy();
 await new Promise(r=>setTimeout(r,100));const disabled=await dial({token:hello.token});await disabled.hello();disabled.sock.write(packet(2,1,0,1));disabled.sock.write(Buffer.from('T'));child.stdin.write('disable\n');await assert.rejects(disabled.read(32));disabled.sock.destroy();
 child.stdin.write('enable\n');await new Promise(r=>setTimeout(r,100));const malformed=await dial({token:hello.token});await malformed.hello();const corrupt=packet(1,1);corrupt[31]=1;malformed.sock.write(corrupt);await assert.rejects(malformed.read(32));malformed.sock.destroy();
 await new Promise(r=>setTimeout(r,100));const idle=await dial({token:hello.token});await idle.hello();await assert.rejects(idle.read(32));idle.sock.destroy();
 await new Promise(r=>setTimeout(r,100));const active=await dial({token:hello.token});await active.hello();active.sock.write(packet(2,1,0,1));child.stdin.write('revoke\n');await assert.rejects(active.read(32));active.sock.destroy();
 await new Promise(r=>setTimeout(r,100));const revoked=await dial({token:hello.token});await assert.rejects(revoked.hello());revoked.sock.destroy();
 times.sort((a,b)=>a-b);const result={platform:process.platform,architecture:process.arch,transport:'TLS 1.3 loopback; dry native dispatcher',samples:100,roundTripMedianMs:times[49],roundTripP95Ms:times[94],tests:['invalid invitation rejected','desktop-approved pairing','exclusive controller','ordered drag/motion/release','100 heartbeat round trips','trusted reconnect','replayed sequence disconnect','disable during held drag and partial frame','malformed packet disconnect','heartbeat watchdog disconnect','active revocation disconnect','revocation rejected'],note:'Loopback software round trip only. Not Android Wi-Fi or perceived pointer latency.'};
 fs.mkdirSync('dist',{recursive:true});fs.writeFileSync('dist/network-smoke.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
}finally{clearTimeout(timer);child.kill();lines.close()}
