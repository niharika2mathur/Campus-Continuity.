// Thin fetch wrapper. All algorithm results come from the Java backend; nothing is computed here.
const BASE = import.meta.env.VITE_API_BASE || '';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`);
    this.status = status; this.fieldErrors = body?.fieldErrors || null;
  }
}

async function call(method, path, body, form) {
  let res;
  try {
    res = await fetch(`${BASE}/api${path}`, {
      method,
      headers: body && !form ? { 'Content-Type': 'application/json' } : undefined,
      body: form ? body : body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, { message: 'Cannot reach the backend. Is the Java server running on port 8080?' });
  }
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

export const api = {
  get: (p) => call('GET', p),
  post: (p, b) => call('POST', p, b ?? {}),
  put: (p, b) => call('PUT', p, b),
  patch: (p, b) => call('PATCH', p, b),
  del: (p) => call('DELETE', p),
  upload: (p, file) => { const f = new FormData(); f.append('file', file); return call('POST', p, f, true); },
  async text(p) {
    const res = await fetch(`${BASE}/api${p}`);
    if (!res.ok) throw new ApiError(res.status, null);
    return res.text();
  },
};
