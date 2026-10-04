// Shared helpers for the static pages. All user data is rendered with textContent, never innerHTML.

export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: body ? { 'Content-Type': 'application/json', ...headers } : headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = res.status === 204 || res.status === 202 ? null : await res.json().catch(() => null);
  if (!res.ok) {
    const error = new Error(data?.error?.message ?? `Request failed (${res.status})`);
    error.status = res.status;
    error.code = data?.error?.code;
    error.fields = data?.error?.fields ?? {};
    throw error;
  }
  return data;
}

/** "Invalid input. phone: must be a valid phone number" */
export function describe(error) {
  const fields = Object.entries(error.fields ?? {}).map(([field, msg]) => `${field}: ${msg}`);
  return [error.message, ...fields].join(' ');
}

/** Tiny element builder: el('td', {className: 'x'}, 'text', childNode). */
export function el(tag, props = {}, ...children) {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...children.filter((c) => c !== null && c !== undefined));
  return node;
}

export function formValues(form) {
  return Object.fromEntries(new FormData(form).entries());
}

export function show(node, text, isError = false) {
  node.textContent = text;
  node.className = isError ? 'error' : 'ok';
}

export function when(iso) {
  return iso ? new Date(iso).toLocaleString() : '';
}
