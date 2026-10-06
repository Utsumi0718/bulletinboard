export type ApiErrorKind = 'http' | 'network' | 'invalid-response'

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | null
  readonly fieldErrors: Record<string, string>
  readonly reason?: string

  constructor(
    kind: ApiErrorKind,
    status: number | null,
    message: string,
    fieldErrors: Record<string, string> = {},
    reason?: string,
  ) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.fieldErrors = fieldErrors
    this.reason = reason
  }
}

export type UserFacingError = {
  category: 'validation' | 'authentication' | 'forbidden' | 'not-found' | 'conflict' | 'server' | 'network' | 'unexpected'
  status: number | null
  message: string
  fieldErrors: Record<string, string>
  reason?: string
}

export function toUserFacingError(error: unknown): UserFacingError {
  if (!(error instanceof ApiError)) {
    return { category: 'unexpected', status: null, message: '処理を完了できませんでした。', fieldErrors: {} }
  }

  const status = error.status
  const category = error.kind === 'network' ? 'network'
    : error.kind === 'invalid-response' ? 'unexpected'
    : status === 400 ? 'validation'
    : status === 401 ? 'authentication'
    : status === 403 ? 'forbidden'
    : status === 404 ? 'not-found'
    : status === 409 ? 'conflict'
    : status !== null && status >= 500 ? 'server'
    : 'unexpected'

  return {
    category,
    status,
    message: error.message,
    fieldErrors: error.fieldErrors,
    reason: error.reason,
  }
}

type ApiRequestOptions = Omit<RequestInit, 'credentials' | 'body'> & {
  body?: BodyInit | null
  json?: unknown
}

export type ApiClient = {
  request<T>(path: string, options?: ApiRequestOptions): Promise<T>
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isJson(response: Response): boolean {
  return /\bjson\b/i.test(response.headers.get('Content-Type') ?? '')
}

async function readJson(response: Response): Promise<unknown> {
  if (!isJson(response)) return null
  try {
    return await response.json()
  } catch {
    return null
  }
}

function fallbackMessage(status: number): string {
  switch (status) {
    case 400: return '入力内容を確認してください。'
    case 401: return 'ログインが必要です。'
    case 403: return 'この操作は許可されていません。'
    case 404: return '対象が見つかりません。'
    case 409: return '現在の状態では操作できません。'
    default: return '処理を完了できませんでした。時間をおいて再度お試しください。'
  }
}

async function responseError(response: Response): Promise<ApiError> {
  const body = await readJson(response)
  const payload = isObject(body) ? body : {}
  const message = response.status < 500 && typeof payload.message === 'string' && payload.message.trim()
    ? payload.message
    : fallbackMessage(response.status)
  const fieldErrors: Record<string, string> = {}
  if (response.status < 500 && isObject(payload.fieldErrors)) {
    for (const [field, value] of Object.entries(payload.fieldErrors)) {
      if (typeof value === 'string') fieldErrors[field] = value
    }
  }
  return new ApiError('http', response.status, message, fieldErrors,
    response.status < 500 && typeof payload.reason === 'string' ? payload.reason : undefined)
}

export function createApiClient(fetchImpl: typeof fetch = globalThis.fetch): ApiClient {
  async function send(path: string, init: RequestInit): Promise<Response> {
    try {
      return await fetchImpl(path, { ...init, credentials: 'include' })
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error
      throw new ApiError('network', null, 'サーバーに接続できません。時間をおいて再度お試しください。')
    }
  }

  return {
    async request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
      if (!path.startsWith('/api/') || path.startsWith('/api//')) {
        throw new TypeError('API path must start with /api/')
      }
      const { json, ...init } = options
      if (json !== undefined && init.body !== undefined) {
        throw new TypeError('json and body cannot be used together')
      }

      const method = (init.method ?? 'GET').toUpperCase()
      const headers = new Headers(init.headers)
      headers.set('Accept', 'application/json')
      if (json !== undefined) headers.set('Content-Type', 'application/json')

      if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const csrfResponse = await send('/api/csrf', {
          method: 'GET', headers: { Accept: 'application/json' }, cache: 'no-store', signal: init.signal,
        })
        if (!csrfResponse.ok) throw await responseError(csrfResponse)
        const csrf = await readJson(csrfResponse)
        if (!isObject(csrf) || typeof csrf.headerName !== 'string' || !csrf.headerName
            || typeof csrf.token !== 'string' || !csrf.token) {
          throw new ApiError('invalid-response', csrfResponse.status, '認証情報を確認できませんでした。')
        }
        headers.set(csrf.headerName, csrf.token)
      }

      const response = await send(path, {
        ...init,
        method,
        headers,
        body: json === undefined ? init.body : JSON.stringify(json),
      })
      if (!response.ok) throw await responseError(response)
      if (response.status === 204 || method === 'HEAD') return undefined as T
      const result = await readJson(response)
      if (result === null) {
        throw new ApiError('invalid-response', response.status, '応答を読み取れませんでした。')
      }
      return result as T
    },
  }
}

export const api = createApiClient()
