import https from 'node:https';
import { readFileSync, writeFileSync, existsSync, renameSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPublicKey, randomBytes } from 'node:crypto';
import { hash, sign, verify, equal, encrypt, decrypt } from './crypto.mjs';

export function createPanelServer({ directory = dirname(fileURLToPath(import.meta.url)), port = 8443, host = '0.0.0.0' } = {}) {
  const dataDir = join(directory, 'data'), config = JSON.parse(readFileSync(join(dataDir, 'config.json')));
  const storageKey = Buffer.from(config.storageKey, 'base64'), statePath = join(dataDir, 'state.bin');
  let state = existsSync(statePath) ? decrypt(storageKey, readFileSync(statePath)) : { devices: {}, events: {} };
  const clients = new Set(), attempts = new Map();
  function save() { const temp = statePath + '.tmp'; writeFileSync(temp, encrypt(storageKey, state), { mode: 0o600 }); renameSync(temp, statePath); }
  const publicKey = createPublicKey(config.privateKey).export({ format: 'der', type: 'spki' }).toString('base64');
  const list = () => Object.values(state.events).map(x => ({ ...JSON.parse(x.manifest), receipt: JSON.parse(x.receipt) })).sort((a, b) => b.created_at.localeCompare(a.created_at));
  const broadcast = () => { for (const res of clients) res.write(`data: ${JSON.stringify(list())}\n\n`); };
  function json(res, code, object) { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(object)); }
  async function body(req) {
    let bytes = 0, chunks = []; for await (const chunk of req) { bytes += chunk.length; if (bytes > 65536) throw Error('size'); chunks.push(chunk); }
    return JSON.parse(Buffer.concat(chunks));
  }
  const server = https.createServer({ key: readFileSync(join(directory, 'certs/key.pem')), cert: readFileSync(join(directory, 'certs/cert.pem')), minVersion: 'TLSv1.2' }, async (req, res) => {
    res.setHeader('Cache-Control', 'no-store'); res.setHeader('X-Content-Type-Options', 'nosniff'); res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Content-Security-Policy', "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    const url = new URL(req.url, 'https://localhost'), token = (req.headers.authorization || '').replace(/^Bearer /, '');
    try {
      if (req.method === 'GET' && ['/', '/app.js', '/style.css'].includes(url.pathname)) {
        const file = { '/': 'index.html', '/app.js': 'app.js', '/style.css': 'style.css' }[url.pathname];
        res.writeHead(200, { 'Content-Type': { 'index.html': 'text/html; charset=utf-8', 'app.js': 'text/javascript; charset=utf-8', 'style.css': 'text/css; charset=utf-8' }[file] });
        return res.end(readFileSync(join(directory, 'public', file)));
      }
      if (req.method === 'POST' && url.pathname === '/api/pair') {
        const ip = req.socket.remoteAddress, now = Date.now(), previous = attempts.get(ip);
        const entry = previous && now - previous.at < 60000 ? previous : { at: now, count: 0 };
        attempts.set(ip, entry); if (++entry.count > 10) return json(res, 429, { error: 'Aguarde antes de parear novamente.' });
        const b = await body(req);
        if (!equal(b.code, config.pairingToken) || !/^[a-zA-Z0-9-]{8,80}$/.test(b.device_id)) return json(res, 401, { error: 'Pareamento não autorizado.' });
        const key = createPublicKey({ key: Buffer.from(b.public_key, 'base64'), type: 'spki', format: 'der' });
        if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails.namedCurve !== 'prime256v1') return json(res, 400, { error: 'Exige chave P-256.' });
        const existing = state.devices[b.device_id];
        if (existing && existing.publicKey !== b.public_key) return json(res, 409, { error: 'Dispositivo já vinculado a outra chave.' });
        const device = existing || { publicKey: b.public_key, token: randomBytes(32).toString('hex'), sequence: 0, lastHash: '' };
        state.devices[b.device_id] = device; save();
        return json(res, 200, { token: device.token, server_public_key: publicKey });
      }
      if (req.method === 'GET' && (url.pathname === '/api/events' || url.pathname === '/api/stream')) {
        if (!equal(token, config.adminToken)) return json(res, 401, { error: 'Acesso não autorizado.' });
        if (url.pathname === '/api/events') return json(res, 200, list());
        res.writeHead(200, { 'Content-Type': 'text/event-stream', Connection: 'keep-alive' });
        res.write(`data: ${JSON.stringify(list())}\n\n`); clients.add(res);
        const keepalive = setInterval(() => res.write(': heartbeat\n\n'), 20000);
        req.on('close', () => { clearInterval(keepalive); clients.delete(res); }); return;
      }
      if (req.method === 'POST' && url.pathname === '/api/events') {
        const b = await body(req), manifest = typeof b.manifest === 'string' ? JSON.parse(b.manifest) : null;
        const device = manifest && state.devices[manifest.device_id];
        if (!device || !equal(token, device.token)) return json(res, 401, { error: 'Dispositivo não autorizado.' });
        const key = createPublicKey({ key: Buffer.from(device.publicKey, 'base64'), type: 'spki', format: 'der' });
        if (!verify(key, b.manifest, b.signature)) return json(res, 400, { error: 'Assinatura inválida.' });
        if (!/^[a-f0-9-]{36}$/.test(manifest.event_id) || !Number.isSafeInteger(manifest.sequence) || !['voice', 'code', 'manual', 'test'].includes(manifest.trigger) || !Number.isFinite(Date.parse(manifest.created_at))) return json(res, 400, { error: 'Manifesto inválido.' });
        const digest = hash(b.manifest), existing = state.events[manifest.event_id];
        if (existing) {
          if (hash(existing.manifest) !== digest) return json(res, 409, { error: 'ID reutilizado com outro conteúdo.' });
          return json(res, 200, { receipt: existing.receipt, signature: existing.signature });
        }
        if (manifest.sequence !== device.sequence + 1 || manifest.previous_manifest_hash !== device.lastHash) return json(res, 409, { error: 'Cadeia divergente. Não foi emitido recibo.' });
        const receipt = JSON.stringify({ event_id: manifest.event_id, manifest_hash: digest, received_at: new Date().toISOString() });
        const receiptSignature = sign(config.privateKey, receipt);
        state.events[manifest.event_id] = { manifest: b.manifest, receipt, signature: receiptSignature };
        device.sequence = manifest.sequence; device.lastHash = digest; save(); broadcast();
        return json(res, 200, { receipt, signature: receiptSignature });
      }
      json(res, 404, { error: 'Recurso não encontrado.' });
    } catch { if (!res.headersSent) json(res, 400, { error: 'Requisição inválida.' }); else res.end(); }
  });
  server.headersTimeout = 15000; server.requestTimeout = 20000;
  return { server, listen: () => new Promise(resolve => server.listen(port, host, () => resolve(server.address()))) };
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const app = createPanelServer({ port: Number(process.env.PORT || 8443) });
  await app.listen(); console.log('Painel local pronto em HTTPS:8443. Sem integração com centrais públicas.');
}
