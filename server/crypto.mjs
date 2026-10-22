import { createHash, createSign, createVerify, randomBytes, createCipheriv, createDecipheriv, timingSafeEqual } from 'node:crypto';
export const hash = bytes => createHash('sha256').update(bytes).digest('hex');
export const sign = (key, bytes) => createSign('SHA256').update(bytes).end().sign(key).toString('base64');
export const verify = (key, bytes, signature) => {
  try { return createVerify('SHA256').update(bytes).end().verify(key, Buffer.from(signature, 'base64')); } catch { return false; }
};
export const equal = (a, b) => typeof a === 'string' && typeof b === 'string' && Buffer.byteLength(a) === Buffer.byteLength(b) && timingSafeEqual(Buffer.from(a), Buffer.from(b));
export function encrypt(key, object) {
  const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', key, iv);
  const data = Buffer.concat([cipher.update(JSON.stringify(object)), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), data]);
}
export function decrypt(key, bytes) {
  const cipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(0, 12));
  cipher.setAuthTag(bytes.subarray(12, 28));
  return JSON.parse(Buffer.concat([cipher.update(bytes.subarray(28)), cipher.final()]).toString());
}
