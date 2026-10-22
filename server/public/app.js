const $ = id => document.getElementById(id);
let token = '', controller;
function render(events) {
  $('total').textContent = events.length; $('events').replaceChildren();
  if (!events.length) { const p = document.createElement('p'); p.textContent = 'Nenhuma Ocorrência recebida. O Painel está aguardando um dispositivo pareado.'; $('events').append(p); }
  for (const event of events) {
    const card = document.createElement('article'); card.className = 'card event' + (event.test ? ' test' : '');
    const head = document.createElement('header'), h = document.createElement('h2'), tag = document.createElement('span');
    h.textContent = event.test ? 'Teste recebido' : 'Ocorrência recebida'; tag.className = 'tag'; tag.textContent = 'ACKNOWLEDGED'; head.append(h, tag); card.append(head);
    const dl = document.createElement('dl');
    const values = [['Horário do dispositivo', new Date(event.created_at).toLocaleString('pt-BR')], ['Recebido no Painel', new Date(event.receipt.received_at).toLocaleString('pt-BR')], ['Origem', event.trigger], ['Dispositivo', event.device_id], ['Ocorrência', event.event_id], ['Manifesto', event.receipt.manifest_hash], ['Assinatura', 'Verificada no recebimento; recibo emitido pelo servidor']];
    for (const [label, value] of values) { const dt = document.createElement('dt'), dd = document.createElement('dd'); dt.textContent = label; dd.textContent = value; dl.append(dt, dd); }
    const dt = document.createElement('dt'), dd = document.createElement('dd'); dt.textContent = 'Localização';
    const loc = event.location;
    if (loc && Number.isFinite(loc.lat) && Number.isFinite(loc.lon) && Math.abs(loc.lat) <= 90 && Math.abs(loc.lon) <= 180) {
      const a = document.createElement('a'); a.href = `https://maps.google.com/?q=${loc.lat},${loc.lon}`; a.rel = 'noreferrer noopener'; a.target = '_blank'; a.textContent = `Abrir mapa externo · precisão aproximada ${Math.round(loc.accuracy)} m`; dd.append(a);
    } else dd.textContent = 'Não incluída';
    dl.append(dt, dd); card.append(dl); $('events').append(card);
  }
}
function renderCalls(calls) {
  $('calls').replaceChildren();
  for (const call of calls) {
    const card = document.createElement('article'); card.className = 'card event';
    const head = document.createElement('header'), h = document.createElement('h2'), tag = document.createElement('span');
    h.textContent = 'Ligação para a Rede de Apoio'; tag.className = 'tag'; tag.textContent = call.done ? 'ENCERRADA' : 'EM ANDAMENTO'; head.append(h, tag); card.append(head);
    const p = document.createElement('p'); p.textContent = call.status; card.append(p);
    const ul = document.createElement('ul');
    for (const a of call.attempts) { const li = document.createElement('li'); li.textContent = `${new Date(a.at).toLocaleTimeString('pt-BR')} · ${a.to} · ${a.result}`; ul.append(li); }
    card.append(ul);
    if (!call.done) {
      const b = document.createElement('button'); b.className = 'secondary'; b.textContent = 'Parar de ligar';
      b.addEventListener('click', () => fetch(`/api/calls/${call.id}/cancel`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } }));
      card.append(b);
    }
    $('calls').append(card);
  }
}
async function connect() {
  controller?.abort(); controller = new AbortController(); $('connection').textContent = 'Conectando…';
  try {
    const response = await fetch('/api/stream', { headers: { Authorization: `Bearer ${token}` }, signal: controller.signal });
    if (!response.ok) throw Error(response.status === 401 ? 'Código de acesso inválido.' : 'Conexão indisponível.');
    $('login').hidden = true; $('workspace').hidden = false; $('connection').textContent = '● Conectado · recebendo em tempo real'; $('error').textContent = '';
    const reader = response.body.getReader(), decoder = new TextDecoder(); let pending = '';
    while (true) {
      const { value, done } = await reader.read(); if (done) throw Error('Conexão encerrada. Reconecte para atualizar.');
      pending += decoder.decode(value, { stream: true }); let end;
      while ((end = pending.indexOf('\n\n')) >= 0) { const line = pending.slice(0, end); pending = pending.slice(end + 2); if (line.startsWith('event: calls\ndata: ')) renderCalls(JSON.parse(line.slice(19))); else if (line.startsWith('data: ')) render(JSON.parse(line.slice(6))); }
    }
  } catch (e) { if (e.name !== 'AbortError') { $('connection').textContent = '○ Desconectado'; $('error').textContent = e.message; $('login').hidden = false; } }
}
$('login-form').addEventListener('submit', e => { e.preventDefault(); token = $('token').value.trim(); $('token').value = ''; connect(); });
$('logout').addEventListener('click', () => { controller?.abort(); token = ''; $('workspace').hidden = true; $('login').hidden = false; $('events').replaceChildren(); });
