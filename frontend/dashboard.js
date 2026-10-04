import { api, describe, el, formValues, show, when } from '/api.js';

const $ = (id) => document.getElementById(id);
const POLL_MS = 10_000;
let current = null; // { id, idempotencyKey } of the inspection being reviewed

async function start() {
  let me;
  try {
    me = await api('/api/auth/me');
  } catch {
    location.href = '/';
    return;
  }
  $('me').textContent = `${me.email} (${me.role})`;
  if (me.role === 'ADMIN') {
    $('users-section').hidden = false;
    loadUsers();
  }
  loadClients();
  loadInspections();
  setInterval(loadInspections, POLL_MS);
}

async function loadInspections() {
  const rows = await api('/api/inspections');
  const awaiting = rows.filter((r) => r.status === 'SUBMITTED').length;
  $('awaiting').textContent = awaiting ? `${awaiting} awaiting your response` : '';
  $('inspections').replaceChildren(
    ...rows.map((r) => {
      const submitted = r.status === 'SUBMITTED';
      const reviewable = submitted || r.status === 'RESPONDED';
      const row = el(
        'tr',
        { className: `${submitted ? 'submitted' : ''} ${reviewable ? 'clickable' : ''}` },
        el('td', {}, r.clientName),
        el('td', {}, r.carModel),
        el('td', {}, r.rego),
        el('td', {}, r.status),
        el('td', {}, when(r.createdAt)),
        el('td', {}, when(r.submittedAt)),
      );
      if (reviewable) row.addEventListener('click', () => openDetail(r.id));
      return row;
    }),
  );
}

async function openDetail(id) {
  const [detail, media] = await Promise.all([
    api(`/api/inspections/${id}`),
    api(`/api/inspections/${id}/media`),
  ]);
  // One key per review, so a double click or retry sends the response once.
  current = { id, idempotencyKey: crypto.randomUUID() };
  const i = detail.inspection;
  $('detail').hidden = false;
  $('detail-title').textContent = `${i.clientName}: ${i.carModel} (${i.rego})`;
  $('detail-mileage').textContent = detail.mileage ?? '';
  $('detail-notes').textContent = detail.conditionNotes ?? '';
  $('detail-media').replaceChildren(
    ...media.map((m) =>
      m.contentType.startsWith('video/')
        ? el('video', { src: m.url, controls: true })
        : el('a', { href: m.url, target: '_blank' }, el('img', { src: m.url, alt: 'Inspection photo' })),
    ),
  );
  if (!media.length) $('detail-media').append(el('span', { className: 'muted' }, 'No photos or video.'));
  $('respond').hidden = i.status !== 'SUBMITTED';
  $('detail-response').textContent = detail.response ? `Response sent: ${detail.response}` : '';
  $('detail-message').textContent = '';
  $('detail').scrollIntoView({ behavior: 'smooth' });
}

$('respond').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api(`/api/inspections/${current.id}/response`, {
      method: 'POST',
      body: formValues(event.target),
      headers: { 'Idempotency-Key': current.idempotencyKey },
    });
    event.target.reset();
    await openDetail(current.id);
    show($('detail-message'), 'Response sent. The customer will get an SMS.');
    loadInspections();
  } catch (error) {
    show($('detail-message'), describe(error), true);
  }
});

async function loadClients() {
  const clients = await api('/api/clients');
  $('clients').replaceChildren(
    ...clients.map((c) => {
      const send = el('button', {}, 'Send inspection link');
      send.addEventListener('click', () => sendLink(c));
      const remove = el('button', { className: 'secondary' }, 'Delete');
      remove.addEventListener('click', () => deleteClient(c));
      return el(
        'tr',
        {},
        el('td', {}, c.name),
        el('td', {}, c.carModel),
        el('td', {}, c.rego),
        el('td', {}, c.phone),
        el('td', {}, send, ' ', remove),
      );
    }),
  );
}

async function sendLink(client) {
  try {
    await api('/api/inspections', { method: 'POST', body: { clientId: client.id } });
    show($('client-message'), `Inspection link sent to ${client.name}.`);
    loadInspections();
  } catch (error) {
    show($('client-message'), describe(error), true);
  }
}

async function deleteClient(client) {
  if (!confirm(`Delete ${client.name}?`)) return;
  try {
    await api(`/api/clients/${client.id}`, { method: 'DELETE' });
    loadClients();
  } catch (error) {
    show($('client-message'), describe(error), true);
  }
}

$('new-client').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/api/clients', { method: 'POST', body: formValues(event.target) });
    event.target.reset();
    show($('client-message'), 'Client added.');
    loadClients();
  } catch (error) {
    show($('client-message'), describe(error), true);
  }
});

async function loadUsers() {
  const users = await api('/api/users');
  $('users').replaceChildren(
    ...users.map((u) =>
      el('tr', {}, el('td', {}, u.email), el('td', {}, u.role), el('td', {}, when(u.createdAt))),
    ),
  );
}

$('new-user').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/api/users', { method: 'POST', body: formValues(event.target) });
    event.target.reset();
    show($('user-message'), 'User created.');
    loadUsers();
  } catch (error) {
    show($('user-message'), describe(error), true);
  }
});

$('logout').addEventListener('click', async () => {
  await api('/api/auth/logout', { method: 'POST' });
  location.href = '/';
});

start();
