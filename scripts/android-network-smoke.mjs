import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {once} from 'node:events';
import {createInterface} from 'node:readline';
import path from 'node:path';
import fs from 'node:fs';
const run=promisify(execFile);
const suffix=process.platform==='win32'?'.exe':'';
const receiver=process.argv[2]||`desktop/core/target/debug/examples/smoke_receiver${suffix}`;
const adb=process.env.ANDROID_HOME?path.join(process.env.ANDROID_HOME,'platform-tools',`adb${suffix}`):`adb${suffix}`;
const child=spawn(receiver,[],{stdio:['pipe','pipe','inherit']});const lines=createInterface({input:child.stdout});
try{
 const [line]=await once(lines,'line');const {snapshot}=JSON.parse(line);const uri=new URL(snapshot.pairing_uri);uri.searchParams.set('host','10.0.2.2');
 await run(adb,['install','-r','android/app/build/outputs/apk/debug/app-debug.apk']);
 await run(adb,['install','-r','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']);
 await run(adb,['shell','input','keyevent','82']);
 // Quote the invitation for adb's remote shell. No credentials from a production receiver are used.
 const command=`am instrument -w -e pairingUri '${uri.toString()}' app.thumbpad.test/androidx.test.runner.AndroidJUnitRunner`;
 const {stdout,stderr}=await run(adb,['shell',command],{timeout:120000,maxBuffer:2*1024*1024});
 fs.mkdirSync('dist/reports',{recursive:true});fs.writeFileSync('dist/reports/android-instrumentation.txt',stdout+stderr);console.log(stdout);
 if(/FAILURES|INSTRUMENTATION_FAILED|Process crashed|shortMsg=/.test(stdout)||!stdout.includes('OK (3 tests)'))throw Error('Android interaction/integration tests did not pass');
 fs.mkdirSync('docs/screenshots',{recursive:true});
 for(const name of ['android-demo.png','android-connected.png','android-scroll-wheel.png'])await run(adb,['pull',`/sdcard/Android/data/app.thumbpad/files/${name}`,`docs/screenshots/${name}`]);
 console.log('PASS: Android install, native Compose interactions, optional scroll wheel, pinned TLS pairing, clicks, drag, reconnect; screenshots saved. Host dispatcher was dry.');
}finally{child.kill();lines.close()}
