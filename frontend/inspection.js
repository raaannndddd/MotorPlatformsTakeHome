import { api, describe, el, formValues, show } from '/api.js';

const $ = (id) => document.getElementById(id);
const token = location.pathname.split('/').pop();
// One key for this page view, so a double tap or retry submits once.
const submitKey = crypto.randomUUID();
let uploadsInFlight = 0;

function step(name) {
  for (const id of ['otp-step', 'form-step', 'done-step']) $(id).hidden = id !== name;
}

async function start() {
  // This call (not the page load itself) is what marks the inspection as opened.
  try {
    await api('/api/public/session', { method: 'POST', body: { token } });
  } catch (error) {
    show($('message'), describe(error), true);
    return;
  }
  try {
    await api('/api/public/inspection'); // Already verified in this browser?
    step('form-step');
  } catch {
    step('otp-step');
  }
}

$('send-code').addEventListener('click', async () => {
  try {
    await api('/api/public/otp', { method: 'POST', body: { token } });
    $('verify').hidden = false;
    $('send-code').textContent = 'Send a new code';
    show($('message'), 'Code sent. It expires in 5 minutes.');
  } catch (error) {
    show($('message'), describe(error), true);
  }
});

$('verify').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/api/public/otp/verify', { method: 'POST', body: { token, ...formValues(event.target) } });
    $('message').textContent = '';
    step('form-step');
  } catch (error) {
    show($('message'), describe(error), true);
  }
});

// Upload: ask for a URL, send the file straight to storage, then confirm.
$('files').addEventListener('change', (event) => {
  for (const file of event.target.files) upload(file);
  event.target.value = '';
});

async function upload(file) {
  const item = el('li', {}, `${file.name}: uploading...`);
  $('uploads').append(item);
  uploadsInFlight++;
  try {
    const ticket = await api('/api/public/media', {
      method: 'POST',
      body: { contentType: file.type, sizeBytes: file.size },
    });
    const put = await fetch(ticket.uploadUrl, {
      method: ticket.method,
      headers: { 'Content-Type': ticket.contentType },
      body: file,
    });
    if (!put.ok) throw new Error(`Upload failed (${put.status})`);
    await api(`/api/public/media/${ticket.mediaId}/confirm`, { method: 'POST' });
    show(item, `${file.name}: uploaded`);
  } catch (error) {
    show(item, `${file.name}: ${describe(error)}`, true);
  } finally {
    uploadsInFlight--;
  }
}

$('submission').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (uploadsInFlight > 0) {
    show($('message'), 'Please wait for your uploads to finish.', true);
    return;
  }
  const values = formValues(event.target);
  $('submit-button').disabled = true;
  try {
    await api('/api/public/submit', {
      method: 'POST',
      body: { mileage: Number(values.mileage), conditionNotes: values.conditionNotes },
      headers: { 'Idempotency-Key': submitKey },
    });
    $('message').textContent = '';
    step('done-step');
  } catch (error) {
    show($('message'), describe(error), true);
    $('submit-button').disabled = false;
  }
});

start();
