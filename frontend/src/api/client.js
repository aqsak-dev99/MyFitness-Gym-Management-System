/**
 * client.js — the ONLY place in this app that calls fetch() directly.
 * Every other file (authApi.js, and every future resource-specific api
 * file) goes through this. Pages never import fetch themselves.
 *
 * Deliberately NOT coupled to React — reads the token straight from
 * localStorage rather than from AuthContext, so this file could be
 * tested or reused completely independently of the React tree above it.
 *
 * Base URL — reads VITE_API_BASE_URL at build time so the exact same
 * code works locally and once deployed, rather than hardcoding one
 * environment's address. Falls back to localhost:8080 when that env
 * var isn't set, so local `npm run dev` needs zero configuration —
 * only a real deployment needs to actually set it.
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * ApiError carries the REAL shape GlobalExceptionHandler actually
 * returns — status, a human-readable message, and (for Bean Validation
 * failures specifically) a fieldErrors map keyed by field name. Callers
 * that need per-field errors (like the Register form) read
 * error.fieldErrors directly; callers that just need one message read
 * error.message.
 */
export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || null;
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const token = localStorage.getItem('token');

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  // 204 No Content (e.g. DELETE endpoints) — nothing to parse.
  if (response.status === 204) return null;

  let data = null;
  try {
    data = await response.json();
  } catch {
    // A non-JSON response (rare, but possible on a network-level
    // failure the backend never even got to format) — fall through
    // with data still null, handled below.
  }

  if (!response.ok) {
    const message = data?.message || response.statusText || 'Request failed.';
    throw new ApiError(response.status, message, data?.fieldErrors);
  }

  return data;
}

export const apiClient = {
  get: (path) => request(path, { method: 'GET' }),
  post: (path, body) => request(path, { method: 'POST', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
};