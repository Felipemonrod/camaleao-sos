import { existsSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { networkInterfaces } from 'node:os';
import { randomBytes, generateKeyPairSync, X509Certificate } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { hash } from './crypto.mjs';
const root = dirname(fileURLToPath(import.meta.url));
mkdirSync(join(root, 'certs'), { recursive: true }); mkdirSync(join(root, 'data'), { recursive: true });
const configPath = join(root, 'data/config.json');
const ips = Object.values(networkInterfaces()).flat().filter(x => x && x.family === 'IPv4' && !x.internal).map(x => x.address);
if (!existsSync(join(root, 'certs/cert.pem'))) {
  const openssl = process.env.OPENSSL || (process.platform === 'win32' ? 'C:/Program Files/Git/usr/bin/openssl.exe' : 'openssl');
  execFileSync(openssl, ['req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-days', '365', '-keyout', join(root, 'certs/key.pem'), '-out', join(root, 'certs/cert.pem'), '-subj', '/CN=Camaleao local', '-addext', `subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2${ips.map(ip => ',IP:' + ip).join('')}`], { stdio: 'ignore' });
}
if (!existsSync(configPath)) {
  const keys = generateKeyPairSync('ec', { namedCurve: 'prime256v1', publicKeyEncoding: { type: 'spki', format: 'pem' }, privateKeyEncoding: { type: 'pkcs8', format: 'pem' } });
  writeFileSync(configPath, JSON.stringify({ adminToken: randomBytes(24).toString('hex'), pairingToken: randomBytes(24).toString('hex'), storageKey: randomBytes(32).toString('base64'), ...keys }, null, 2), { mode: 0o600 });
}
const config = JSON.parse(readFileSync(configPath));
const certificate = new X509Certificate(readFileSync(join(root, 'certs/cert.pem')));
const pin = hash(certificate.raw);
const url = `https://${ips[0] || '127.0.0.1'}:8443`;
const pairing = Buffer.from(JSON.stringify({ url, pin, code: config.pairingToken })).toString('base64');
writeFileSync(join(root, 'data/pareamento.txt'), pairing, { mode: 0o600 });
console.log(`Painel: ${url}\nAcesso ao Painel: ${config.adminToken}\nCódigo de pareamento Android (copie a linha):\n${pairing}\nEmulador: substitua a URL por https://10.0.2.2:8443 na Área protegida.\nCertificado SHA-256: ${pin}\nGuarde estes segredos apenas na equipe autorizada. Inicie: npm start`);
