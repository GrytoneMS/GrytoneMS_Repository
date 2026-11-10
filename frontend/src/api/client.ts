import type { ApiFailure, ApiResponse, FieldErrors } from '../types/api'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors?: FieldErrors | null

  constructor(status: number, failure: ApiFailure) {
    super(failure.message)
    this.name = 'ApiError'
    this.status = status
    this.code = failure.code
    this.fieldErrors = failure.fieldErrors
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  token?: string
}

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== 'object' || value === null || !('success' in value)) return false
  if (value.success === true) return 'data' in value
  if (value.success !== false || !('code' in value) || !('message' in value)) return false
  if (typeof value.code !== 'string' || typeof value.message !== 'string') return false
  if (!('fieldErrors' in value) || value.fieldErrors == null) return true
  if (Array.isArray(value.fieldErrors)) {
    return value.fieldErrors.every((error: unknown) => typeof error === 'object' && error !== null
      && 'field' in error && typeof error.field === 'string'
      && 'message' in error && typeof error.message === 'string')
  }
  return typeof value.fieldErrors === 'object' && !Array.isArray(value.fieldErrors)
    && Object.values(value.fieldErrors).every((message) => typeof message === 'string')
}

/** Relative API paths only. Callers explicitly supply tokens; no persistence policy is assumed. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
  if (!baseUrl) throw new Error('VITE_API_BASE_URL 환경 변수를 설정해 주세요.')
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) {
    throw new Error('API 경로는 /로 시작하는 상대 경로여야 합니다.')
  }
  const { token, body, headers: initialHeaders, ...init } = options
  const headers = new Headers(initialHeaders)
  headers.set('Accept', 'application/json')
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  // Network errors and AbortError propagate unchanged to the caller.
  const response = await fetch(`${baseUrl.replace(/\/+$/, '')}${path}`, {
    ...init,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    throw new ApiError(response.status, {
      success: false, code: 'INVALID_RESPONSE', message: '서버가 JSON 응답을 반환하지 않았습니다.',
    })
  }
  if (!isEnvelope(payload)) {
    throw new ApiError(response.status, {
      success: false, code: 'INVALID_RESPONSE', message: '서버 응답 형식을 확인해 주세요.',
    })
  }
  if (!payload.success) throw new ApiError(response.status, payload)
  if (!response.ok) {
    throw new ApiError(response.status, {
      success: false, code: 'HTTP_ERROR', message: `HTTP 요청 실패 (${response.status})`,
    })
  }
  // T is the caller's contract; endpoint-specific data validation belongs to its API module.
  return payload.data as T
}
