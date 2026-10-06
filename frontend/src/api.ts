// Shared helpers for the pages. React escapes everything it renders, so user data is never HTML.

export class ApiError extends Error {
  status: number;
  code?: string;
  fields: Record<string, string>;

  constructor(message: string, status: number, code?: string, fields: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

/** The one error shape every endpoint returns. */
interface ErrorBody {
  error?: { code?: string; message?: string; fields?: Record<string, string> };
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
}

export async function api<T = void>(
  path: string,
  { method = 'GET', body, headers = {} }: RequestOptions = {},
): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: body ? { 'Content-Type': 'application/json', ...headers } : headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = res.status === 204 || res.status === 202 ? null : await res.json().catch(() => null);
  if (!res.ok) {
    const error = (data as ErrorBody | null)?.error;
    throw new ApiError(
      error?.message ?? `Request failed (${res.status})`,
      res.status,
      error?.code,
      error?.fields,
    );
  }
  return data as T;
}

/** "Invalid input. phone: must be a valid phone number" */
export function describe(error: unknown): string {
  if (!(error instanceof Error)) return String(error);
  const fields = error instanceof ApiError ? Object.entries(error.fields) : [];
  return [error.message, ...fields.map(([field, msg]) => `${field}: ${msg}`)].join(' ');
}

/** The form's text fields by name. Read it before any await: React clears currentTarget. */
export function formValues(form: HTMLFormElement): Record<string, string> {
  return Object.fromEntries(new FormData(form)) as Record<string, string>;
}

export function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '';
}
