import { execFile, spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';

// Ligação com voz sintetizada a partir do notebook, sem provedor pago. Um Android de
// discagem ligado por USB faz a chamada (adb), o notebook fala o aviso pelos próprios
// alto-falantes ao lado dele e o estado preciso da chamada diz se ela foi atendida.
// Caixa postal também conta como atendida: a telefonia não distingue as duas.
// DISCADOR=adb usa o aparelho de DISCADOR_SERIAL; DISCADOR=simulado só registra.

const ACTIVE = 1, IDLE = 0;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
export const phoneOk = phone => /^\+?\d{8,15}$/.test(phone);
export const mask = phone => '•••' + phone.slice(-4);

function adb(serial, args) {
  return new Promise((resolve, reject) =>
    execFile(process.env.ADB || 'adb', ['-s', serial, ...args], { timeout: 10000 }, (error, stdout) => error ? reject(error) : resolve(stdout)));
}

/** Maior estado "Foreground call state" entre os chips: 1 é ACTIVE, 0 é IDLE. */
export function foregroundState(dump) {
  const states = [...dump.matchAll(/Foreground call state: (-?\d+)/g)].map(m => Number(m[1]));
  return states.includes(ACTIVE) ? ACTIVE : Math.max(IDLE, ...states);
}

function speak(text) {
  // O texto vai por variável de ambiente, nunca concatenado no comando.
  const env = { ...process.env, CAMALEAO_TTS: text };
  const child = process.platform === 'win32'
    ? spawn('powershell', ['-NoProfile', '-Command', "Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'pt-BR' } | Select-Object -First 1; if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }; $s.Rate = -1; $s.Speak($env:CAMALEAO_TTS)"], { env, stdio: 'ignore' })
    : spawn(process.platform === 'darwin' ? 'say' : 'espeak', process.platform === 'darwin' ? [text] : ['-v', 'pt-br', text], { stdio: 'ignore' });
  return child;
}

function adbDriver(serial, { ringMs, pollMs }) {
  const state = async () => foregroundState(await adb(serial, ['shell', 'dumpsys', 'telephony.registry']));
  const hangup = () => adb(serial, ['shell', 'input', 'keyevent', 'KEYCODE_ENDCALL']).catch(() => {});
  return {
    label: `adb ${mask(serial)}`,
    async call(phone, message, job) {
      await adb(serial, ['shell', 'am', 'start', '-a', 'android.intent.action.CALL', '-d', `tel:${phone}`]);
      let answered = false, dialing = false;
      for (const end = Date.now() + ringMs; Date.now() < end && !job.cancelled; await sleep(pollMs)) {
        const s = await state();
        if (s === ACTIVE) { answered = true; break; }
        if (s > IDLE) dialing = true; else if (dialing) break; // recusada, ocupada ou sem sinal
      }
      if (!answered) { await hangup(); return false; }
      // Repete o aviso enquanto a outra ponta estiver na linha, até três vezes.
      for (let i = 0; i < 3 && !job.cancelled && (await state()) === ACTIVE; i++) {
        const voice = speak(message); await new Promise(resolve => voice.on('close', resolve));
      }
      await hangup();
      return true;
    },
  };
}

function simulatedDriver(pollMs) {
  // Atende na segunda rodada, no primeiro número: exercita o retry sem telefonia.
  return { label: 'simulado', async call(phone, message, job) { await sleep(pollMs); return job.round >= 2 && phone === job.phones[0]; } };
}

export function createDialer({ mode = process.env.DISCADOR, serial = process.env.DISCADOR_SERIAL, ringMs = 35000, pollMs = 1000, gapMs = 10000, onChange = () => {} } = {}) {
  const driver = mode === 'adb' && serial ? adbDriver(serial, { ringMs, pollMs }) : mode === 'simulado' ? simulatedDriver(pollMs) : null;
  const jobs = new Map();
  const view = job => ({ id: job.id, device_id: job.deviceId, started_at: job.startedAt, status: job.status, round: job.round, attempts: job.attempts, done: job.done });
  async function run(job) {
    // Não para de ligar até alguém atender ou o Painel cancelar.
    for (job.round = 1; !job.cancelled; job.round++) {
      for (const phone of job.phones) {
        if (job.cancelled) break;
        job.status = `Chamando ${mask(phone)} · rodada ${job.round}`; onChange();
        let answered = false;
        try { answered = await driver.call(phone, job.message, job); } catch (e) { job.attempts.push({ to: mask(phone), at: new Date().toISOString(), result: 'erro do discador' }); continue; }
        job.attempts.push({ to: mask(phone), at: new Date().toISOString(), result: answered ? 'atendida' : 'sem resposta' });
        if (answered) { job.status = `Atendida por ${mask(phone)} (pode ter sido caixa postal)`; job.done = true; return onChange(); }
      }
      if (!job.cancelled) { job.status = `Ninguém atendeu na rodada ${job.round}. Nova rodada em ${gapMs / 1000} s`; onChange(); await sleep(gapMs); }
    }
    job.status = 'Cancelada no Painel'; job.done = true; onChange();
  }
  return {
    available: () => driver !== null,
    label: () => driver?.label || 'indisponível',
    start({ deviceId, phones, message }) {
      const job = { id: randomUUID(), deviceId, phones, message, startedAt: new Date().toISOString(), status: 'Iniciando', round: 0, attempts: [], cancelled: false, done: false };
      jobs.set(job.id, job); run(job); return view(job);
    },
    cancel(id) { const job = jobs.get(id); if (!job || job.done) return false; job.cancelled = true; return true; },
    list: () => [...jobs.values()].map(view).reverse(),
  };
}
