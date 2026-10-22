import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { generateKeyPairSync, randomBytes, randomUUID, createPublicKey } from 'node:crypto';
import { createPanelServer } from '../server.mjs';
import { sign } from '../crypto.mjs';
import { foregroundState, phoneOk } from '../discador.mjs';
const root=dirname(dirname(fileURLToPath(import.meta.url)));
const pair=()=>generateKeyPairSync('ec',{namedCurve:'prime256v1',publicKeyEncoding:{type:'spki',format:'pem'},privateKeyEncoding:{type:'pkcs8',format:'pem'}});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
test('estado preciso da chamada vem do dumpsys do S25',()=>{
  const idle='mPreciseCallState=Ringing call state: -1, Foreground call state: -1, Background call state: -1\n    mPreciseCallState=Ringing call state: 0, Foreground call state: 0, Background call state: 0';
  assert.equal(foregroundState(idle),0);assert.equal(foregroundState(idle.replace('Foreground call state: 0','Foreground call state: 4')),4);
  assert.equal(foregroundState(idle.replace('Foreground call state: 0','Foreground call state: 1')),1);
});
test('número com caractere de shell é recusado',()=>{for(const bad of ['119;reboot','11 9999','$(id)','12'])assert.equal(phoneOk(bad),false);assert.equal(phoneOk('+5511999998888'),true)});
for(const mode of ['simulado',undefined])test(`pedido de ligação assinado com discador ${mode||'desligado'}`,async t=>{
  const dir=mkdtempSync(join(tmpdir(),'camaleao-call-'));mkdirSync(join(dir,'certs'));mkdirSync(join(dir,'data'));
  for(const name of ['cert.pem','key.pem'])copyFileSync(join(root,'certs',name),join(dir,'certs',name));
  const config={...pair(),storageKey:randomBytes(32).toString('base64'),adminToken:randomBytes(24).toString('hex'),pairingToken:randomBytes(24).toString('hex')};
  writeFileSync(join(dir,'data/config.json'),JSON.stringify(config));
  const app=createPanelServer({directory:dir,port:0,host:'127.0.0.1',dialer:{mode,pollMs:20,gapMs:20}}),address=await app.listen(),ca=readFileSync(join(dir,'certs/cert.pem'));
  const req=(path,body,token='')=>new Promise((resolve,reject)=>{const r=https.request({hostname:'localhost',port:address.port,path,method:body?'POST':'GET',ca,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token}},res=>{let text='';res.on('data',c=>text+=c);res.on('end',()=>resolve({status:res.statusCode,body:JSON.parse(text)}))});r.on('error',reject);r.end(body?JSON.stringify(body):undefined)});
  try{
    const keys=pair(),deviceId=randomUUID(),publicKey=createPublicKey(keys.publicKey).export({type:'spki',format:'der'}).toString('base64');
    const token=(await req('/api/pair',{device_id:deviceId,public_key:publicKey,code:config.pairingToken})).body.token;
    const call=over=>{const request=JSON.stringify({device_id:deviceId,request_id:randomUUID(),created_at:new Date().toISOString(),phones:['+5511999990001','+5511999990002'],message:'Alerta do Camaleão.',...over});return{request,signature:sign(keys.privateKey,request)}};
    await t.test('sem token é recusado',async()=>assert.equal((await req('/api/call',call())).status,401));
    await t.test('pedido alterado é recusado',async()=>{const c=call();c.request=c.request.replace('0001','0009');assert.equal((await req('/api/call',c,token)).status,400)});
    await t.test('pedido antigo é recusado',async()=>assert.equal((await req('/api/call',call({created_at:new Date(Date.now()-600000).toISOString()}),token)).status,400));
    if(!mode)return await t.test('sem discador responde 503 para o aparelho ligar sozinho',async()=>assert.equal((await req('/api/call',call(),token)).status,503));
    const c=call(),first=await req('/api/call',c,token);assert.equal(first.status,200);
    await t.test('repetição devolve a mesma ligação',async()=>assert.deepEqual(await req('/api/call',c,token),first));
    await sleep(400);
    const state=readFileSync(join(dir,'data/state.bin'));assert.equal(state.includes(Buffer.from('999990001')),false);
    await t.test('liga em rodadas até alguém atender',async()=>{
      const second=await req('/api/call',call(),token);await req(`/api/calls/${second.body.call_id}/cancel`,{},config.adminToken);await sleep(100);
      assert.equal((await req(`/api/calls/${second.body.call_id}/cancel`,{},'errado')).status,401);
      const jobs=(await req('/api/calls',null,config.adminToken)).body,done=jobs.find(j=>j.id===first.body.call_id),stop=jobs.find(j=>j.id===second.body.call_id);
      assert.match(done.status,/^Atendida por •••0001/);assert.deepEqual(done.attempts.map(a=>a.result),['sem resposta','sem resposta','atendida']);
      assert.equal(stop.status,'Cancelada no Painel');assert.equal(JSON.stringify(jobs).includes('999990001'),false);
    });
  }finally{app.server.closeAllConnections();await new Promise(r=>app.server.close(r))}
});
