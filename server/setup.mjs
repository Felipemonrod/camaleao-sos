import { existsSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { networkInterfaces } from 'node:os';
import { randomBytes, generateKeyPairSync, X509Certificate } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { hash } from './crypto.mjs';
// Uso: node setup.mjs [--ip 192.168.x.y] [--renovar]
// --ip escolhe o endereço que vai no código de pareamento; --renovar gera certificado novo
// (necessário quando a rede muda; o celular precisa parear de novo porque o pin muda).
const root = dirname(fileURLToPath(import.meta.url));
const arg = name => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
mkdirSync(join(root, 'certs'), { recursive: true }); mkdirSync(join(root, 'data'), { recursive: true });
const configPath = join(root, 'data/config.json'), certPath = join(root, 'certs/cert.pem');
// Adaptadores virtuais costumam vir primeiro na lista do Windows e não são alcançáveis pelo celular.
const virtual = /vEthernet|VirtualBox|VMware|Hyper-V|WSL|Loopback|Tailscale|ZeroTier|vpn|docker/i;
const interfaces = Object.entries(networkInterfaces()).flatMap(([name, list]) => (list || []).filter(x => x.family === 'IPv4' && !x.internal).map(x => ({ name, address: x.address, virtual: virtual.test(name) || x.address.startsWith('169.254.') })));
const ips = interfaces.map(x => x.address);
const chosen = arg('--ip') || (interfaces.find(x => !x.virtual) || interfaces[0] || { address: '127.0.0.1' }).address;
if (process.argv.includes('--renovar')) { rmSync(certPath, { force: true }); rmSync(join(root, 'certs/key.pem'), { force: true }); }
if (!existsSync(certPath)) {
  const openssl = process.env.OPENSSL || (process.platform === 'win32' ? 'C:/Program Files/Git/usr/bin/openssl.exe' : 'openssl');
  const all = [...new Set([...ips, chosen])];
  execFileSync(openssl, ['req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-days', '365', '-keyout', join(root, 'certs/key.pem'), '-out', certPath, '-subj', '/CN=Camaleao local', '-addext', `subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2${all.map(ip => ',IP:' + ip).join('')}`], { stdio: 'ignore' });
}
if (!existsSync(configPath)) {
  const keys = generateKeyPairSync('ec', { namedCurve: 'prime256v1', publicKeyEncoding: { type: 'spki', format: 'pem' }, privateKeyEncoding: { type: 'pkcs8', format: 'pem' } });
  writeFileSync(configPath, JSON.stringify({ adminToken: randomBytes(24).toString('hex'), pairingToken: randomBytes(24).toString('hex'), storageKey: randomBytes(32).toString('base64'), ...keys }, null, 2), { mode: 0o600 });
}
const config = JSON.parse(readFileSync(configPath));
const certificate = new X509Certificate(readFileSync(certPath));
const pin = hash(certificate.raw);
const url = `https://${chosen}:8443`;
const pairing = Buffer.from(JSON.stringify({ url, pin, code: config.pairingToken })).toString('base64');
writeFileSync(join(root, 'data/pareamento.txt'), pairing, { mode: 0o600 });
console.log('Redes deste computador:');
for (const x of interfaces) console.log(`  ${x.address === chosen ? '→' : ' '} ${x.address.padEnd(15)} ${x.name}${x.virtual ? ' (virtual)' : ''}`);
if (!(certificate.subjectAltName || '').includes(`IP Address:${chosen}`))
  console.log(`\nATENÇÃO: o certificado não inclui ${chosen}. Rode "node setup.mjs --ip ${chosen} --renovar" e pareie o celular de novo.`);
console.log(`\nPainel: ${url}\nAcesso ao Painel: ${config.adminToken}\nCódigo de pareamento Android (copie a linha):\n${pairing}\nEmulador: substitua a URL por https://10.0.2.2:8443 na Área protegida.\nCertificado SHA-256: ${pin}\nGuarde estes segredos apenas na equipe autorizada. Inicie: npm start`);
