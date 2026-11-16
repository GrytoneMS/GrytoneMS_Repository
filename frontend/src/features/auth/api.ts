import { ApiError, apiRequest } from '../../api/client'

type LoginInput = { email: string; password: string }
type SignupInput = LoginInput & {
  nickname: string
  agreeTerms: boolean
  agreePrivacy: boolean
}

type AuthResponse = { accessToken: string }

const tokenKey = 'teamon.accessToken'

export function login(body: LoginInput) {
  return apiRequest<AuthResponse>('/api/auth/login', { method: 'POST', body })
}

export function signup(body: SignupInput) {
  return apiRequest<AuthResponse>('/api/auth/signup', { method: 'POST', body })
}

export function readAccessToken() {
  return sessionStorage.getItem(tokenKey) ?? localStorage.getItem(tokenKey)
}

export function saveAccessToken(token: string, remember: boolean) {
  if (typeof token !== 'string' || !token.trim()) {
    throw new Error('서버에서 로그인 토큰을 받지 못했어요.')
  }

  const target = remember ? localStorage : sessionStorage
  const other = remember ? sessionStorage : localStorage
  target.setItem(tokenKey, token)
  other.removeItem(tokenKey)
}

export function authErrors(error: unknown): { fields?: Record<string, string>; message?: string } {
  if (!(error instanceof ApiError)) {
    return {
      message: error instanceof TypeError
        ? '서버에 연결할 수 없어요. 백엔드가 실행 중인지 확인해 주세요.'
        : error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.',
    }
  }

  const fields = Array.isArray(error.fieldErrors)
    ? Object.fromEntries(error.fieldErrors.map(({ field, message }) => [field, message]))
    : error.fieldErrors ?? {}

  if (error.code === 'EMAIL_DUPLICATED') fields.email = '이미 가입된 이메일입니다.'
  if (error.code === 'NICKNAME_DUPLICATED') fields.nickname = '이미 사용 중인 닉네임입니다.'
  return { fields, message: Object.keys(fields).length ? undefined : error.message }
}
