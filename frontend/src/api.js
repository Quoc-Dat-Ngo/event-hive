export const API_BASE = 'http://localhost:8181/api/v1'

// The access token lives in memory only; the refresh token is an httpOnly
// cookie scoped to /api/v1/auth, so every request sends credentials.
let accessToken = null
let onTokenChange = () => {}

export function setAccessToken(token) {
  accessToken = token
  onTokenChange(token)
}

export function subscribeToken(listener) {
  onTokenChange = listener
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed with status ${status}`)
    this.status = status
    this.body = body
  }
}

// Spring rejects an expired bearer token even on permit-all endpoints,
// so the public auth endpoints are called without one.
const PUBLIC_PATHS = ['/auth/login', '/auth/register', '/auth/refresh-token']

async function send(method, path, body) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken && !PUBLIC_PATHS.includes(path)) headers.Authorization = `Bearer ${accessToken}`

  return fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

async function parse(res) {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

// Refresh tokens rotate on use, so concurrent callers must share one request
let pendingRefresh = null

export function refreshAccessToken() {
  pendingRefresh ??= (async () => {
    try {
      const res = await send('POST', '/auth/refresh-token')
      const data = res.ok ? await parse(res) : null
      setAccessToken(data?.accessToken ?? null)
      return data?.accessToken ?? null
    } finally {
      pendingRefresh = null
    }
  })()
  return pendingRefresh
}

export async function request(method, path, body) {
  let res = await send(method, path, body)

  // Access tokens expire after 15 minutes; retry once with a rotated token
  if (res.status === 401 && accessToken && !PUBLIC_PATHS.includes(path)) {
    if (await refreshAccessToken()) res = await send(method, path, body)
  }

  const data = await parse(res)
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path),
}

export function decodeToken(token) {
  if (!token) return null
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(payload))
    return {
      userId: claims.userId,
      email: claims.sub,
      roles: claims.roles ?? [],
    }
  } catch {
    return null
  }
}
