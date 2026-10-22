import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { createPanelServer } from '../server.mjs';
import { hash, sign, verify, encrypt, decrypt } from '../crypto.mjs';
const root=dirname(dirname(fileURLToPath(import.meta.url)));
const pair=()=>generateKeyPairSync('ec',{namedCurve:'prime256v1',publicKeyEncoding:{type:'spki',format:'pem'},privateKeyEncoding:{type:'pkcs8',format:'pem'}});
test('AES-GCM rejeita alteração e não expõe dados',()=>{
  const key=randomBytes(32), data=encrypt(key,{secret:'frase privada'});
  assert.equal(data.includes(Buffer.from('frase privada')),false); assert.equal(decrypt(key,data).secret,'frase privada');
  data[data.length-1]^=1;assert.throws(()=>decrypt(key,data));
});
test('Painel HTTPS valida autenticação, assinatura, cadeia e repetição',async t=>{
  const dir=mkdtempSync(join(tmpdir(),'camaleao-test-'));mkdirSync(join(dir,'certs'));mkdirSync(join(dir,'data'));
  for(const name of ['cert.pem','key.pem'])copyFileSync(join(root,'certs',name),join(dir,'certs',name));
  const serverKeys=pair(),config={...serverKeys,storageKey:randomBytes(32).toString('base64'),adminToken:randomBytes(24).toString('hex'),pairingToken:randomBytes(24).toString('hex')};
  writeFileSync(join(dir,'data/config.json'),JSON.stringify(config));
  const app=createPanelServer({directory:dir,port:0,host:'127.0.0.1'}),address=await app.listen();
  const ca=readFileSync(join(dir,'certs/cert.pem'));
  async function req(path,body,token=''){
    return new Promise((resolve,reject)=>{const request=https.request({hostname:'localhost',port:address.port,path,method:body?'POST':'GET',ca,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token}},res=>{let text='';res.on('data',chunk=>text+=chunk);res.on('end',()=>resolve({status:res.statusCode,body:JSON.parse(text)}))});request.on('error',reject);request.end(body?JSON.stringify(body):undefined)});
  }
  try{
    const deviceKeys=pair(),deviceId=randomUUID();
    const {createPublicKey}=await import('node:crypto');const publicKey=createPublicKey(deviceKeys.publicKey).export({type:'spki',format:'der'}).toString('base64');
    await t.test('pareamento exige segredo',async()=>assert.equal((await req('/api/pair',{device_id:deviceId,public_key:publicKey,code:'errado'})).status,401));
    const paired=await req('/api/pair',{device_id:deviceId,public_key:publicKey,code:config.pairingToken});assert.equal(paired.status,200);const token=paired.body.token;
    const manifest=JSON.stringify({event_id:randomUUID(),device_id:deviceId,sequence:1,previous_manifest_hash:'',created_at:new Date().toISOString(),trigger:'test',test:true,location:null});
    let accepted;
    await t.test('verifica assinatura e emite recibo verificável',async()=>{
      accepted=await req('/api/events',{manifest,signature:sign(deviceKeys.privateKey,manifest)},token);assert.equal(accepted.status,200);
      assert.equal(verify(serverKeys.publicKey,accepted.body.receipt,accepted.body.signature),true);assert.equal(JSON.parse(accepted.body.receipt).manifest_hash,hash(manifest));
    });
    await t.test('repetição retorna o mesmo recibo sem duplicar',async()=>{const repeat=await req('/api/events',{manifest,signature:sign(deviceKeys.privateKey,manifest)},token);assert.deepEqual(repeat,accepted)});
    await t.test('alteração de manifesto sem nova assinatura é rejeitada',async()=>{const altered=manifest.replace('"sequence":1','"sequence":2');assert.equal((await req('/api/events',{manifest:altered,signature:sign(deviceKeys.privateKey,manifest)},token)).status,400)});
    await t.test('buraco na cadeia não recebe confirmação',async()=>{const broken=JSON.stringify({...JSON.parse(manifest),event_id:randomUUID(),sequence:3});assert.equal((await req('/api/events',{manifest:broken,signature:sign(deviceKeys.privateKey,broken)},token)).status,409)});
    await t.test('Painel exige autorização e banco fica cifrado',async()=>{
      assert.equal((await req('/api/events')).status,401);const events=await req('/api/events',null,config.adminToken);assert.equal(events.body.length,1);
      assert.equal(readFileSync(join(dir,'data/state.bin')).includes(Buffer.from(deviceId)),false);
    });
  }finally{app.server.closeAllConnections();await new Promise(resolve=>app.server.close(resolve))}
});
