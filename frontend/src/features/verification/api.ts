import { apiRequest } from '../../api/client'

type SchoolSummary = { id: number; name: string; campus: string | null }
export type School = SchoolSummary & { emailDomain: string }
export type Account = {
  status: 'UNVERIFIED' | 'VERIFIED' | 'SUSPENDED' | 'WITHDRAWN'
  reverifyDueDate?: string | null
}
export type Verification = {
  method: 'EMAIL' | 'DOCUMENT'
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  school: SchoolSummary
  rejectReason: string | null
}
export type CodeDelivery = {
  schoolEmail: string
  expiresInSeconds: number
  remainingSendCount: number
}

export function listSchools(signal: AbortSignal) {
  return apiRequest<School[]>('/api/schools', { signal })
}

export function getAccount(token: string, signal: AbortSignal) {
  return apiRequest<Account>('/api/users/me', { token, signal })
}

export function getVerification(token: string, signal: AbortSignal) {
  return apiRequest<Verification | null>('/api/verifications/me', { token, signal })
}

export function sendCode(token: string, schoolId: number, schoolEmail: string) {
  return apiRequest<CodeDelivery>('/api/verifications/school-email', {
    method: 'POST',
    token,
    body: { schoolId, schoolEmail },
  })
}

export function confirmCode(token: string, code: string) {
  return apiRequest<Account>('/api/verifications/school-email/confirm', {
    method: 'POST',
    token,
    body: { code },
  })
}

export function submitDocument(token: string, schoolId: number, file: File) {
  const body = new FormData()
  body.append('schoolId', String(schoolId))
  body.append('file', file)
  return apiRequest<{ verificationId: number; status: 'PENDING' }>('/api/verifications/document', {
    method: 'POST',
    token,
    body,
  })
}
