import { ApiError } from '../../api/client'
import { isPostMock } from '../posts/source'
import { actOnApplication, getMyApplications, validatePage } from './api'
import type { MyApplicationsService } from './controller'
import type { MyApplication, Session } from './model'

export const listExamples = { normal: '여러 상태·다음 페이지', empty: '빈 목록', error: '최초 조회 실패', moreError: '추가 조회 실패', slow: '느린 조회', unknown: '알 수 없는 지원 상태', restrictedPosts: '마감·숨김·삭제된 글' }
export const actionExamples = { success: '정상 처리', failure: '서버 실패', network: '결과 불명확', changed: '지원 상태 변경으로 거절', refreshFailure: '성공 후 재조회 실패', expired: '인증 만료', forbidden: '권한 없음', noAuth: '토큰 없음' }
export function exampleMode(params: URLSearchParams) { return isPostMock && params.get('example') === 'true' }
const exampleSessions = new Map<string, { rows: MyApplication[]; processed: boolean; refreshFailed: boolean }>()
function fail(status: number, code: string, message: string): never { throw new ApiError(status, { success: false, code, message }) }
function fixture(): MyApplication[] {
  return [
    { id: 108, status: 'ACCEPTED', roleName: '영상 편집', post: { id: 1, title: '○○ 영상 공모전 팀원을 구해요', deleted: false }, createdAt: '2026-11-12T15:30:00', decidedAt: '2026-11-13T10:00:00' },
    { id: 107, status: 'PENDING', roleName: '기획', post: { id: 5, title: '광고 마케팅 공모전 기획팀', deleted: false }, createdAt: '2026-11-12T13:00:00', decidedAt: null },
    { id: 106, status: 'CLOSED', roleName: '디자인', post: { id: 4, title: '앱 UI 스프린트 2주', deleted: false }, createdAt: '2026-11-11T12:00:00', decidedAt: '2026-11-12T12:00:00' },
    { id: 105, status: 'REJECTED', roleName: '개발', post: { id: 3, title: null, deleted: true }, createdAt: '2026-11-10T12:00:00', decidedAt: '2026-11-11T12:00:00' },
    { id: 104, status: 'CANCELED', roleName: '스터디원', post: { id: 2, title: '토익 800+ 같이 달려요', deleted: false }, createdAt: '2026-11-09T12:00:00', decidedAt: '2026-11-10T12:00:00' },
    { id: 103, status: 'LEFT', roleName: '팀원', post: { id: 6, title: '브랜딩 포트폴리오 작업', deleted: false }, createdAt: '2026-11-08T12:00:00', decidedAt: '2026-11-09T12:00:00' },
    // 모집글 마감과 지원 ACCEPTED는 별개입니다. post.status를 목록 DTO에 추가하지 않습니다.
    { id: 102, status: 'ACCEPTED', roleName: '팀원', post: { id: 12, title: 'UX 리서치 입문', deleted: false }, createdAt: '2026-11-07T12:00:00', decidedAt: '2026-11-08T12:00:00' },
    { id: 101, status: 'PENDING', roleName: '개발', post: { id: 3, title: '해커톤 같이 나갈 개발자', deleted: false }, createdAt: '2026-11-06T12:00:00', decidedAt: null },
  ]
}
function pause(signal: AbortSignal, ms: number) {
  return new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, ms)
    if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true })
  })
}
export function myApplicationsService(params: URLSearchParams, session?: Session): MyApplicationsService {
  if (!exampleMode(params)) return {
    load: (status, cursor, signal) => getMyApplications(status, cursor, { signal, token: session?.token }),
    act: (id, action, signal) => actOnApplication(id, action, { signal, token: session?.token }),
  }
  const scenario = params.get('list') ?? 'normal', outcome = params.get('result') ?? 'success', viewer = params.get('viewer') ?? 'alice'
  const key = JSON.stringify([scenario, outcome, viewer, params.get('reset')])
  if (!exampleSessions.has(key)) {
    let rows = fixture()
    if (scenario === 'unknown') rows.unshift({ ...rows[0], id: 109, status: 'FUTURE_STATE' })
    if (scenario === 'restrictedPosts') rows = [rows[6], rows[3], { ...rows[7], post: { id: 999, title: '숨김 처리된 모집글 예시', deleted: false } }]
    if (viewer === 'bob') rows = [{ ...fixture()[1], id: 900, post: { id: 8, title: '다른 사용자의 영상 편집 스터디', deleted: false } }]
    exampleSessions.set(key, { rows, processed: false, refreshFailed: false })
  }
  const example = exampleSessions.get(key)!
  return {
    async load(status, cursor, signal) {
      await pause(signal, scenario === 'slow' ? 3000 : 250)
      if (viewer === 'guest') fail(401, 'UNAUTHORIZED', '로그인이 필요해요.')
      if (example.processed && outcome === 'refreshFailure' && !example.refreshFailed) { example.refreshFailed = true; fail(500, 'INTERNAL_ERROR', '개발 예시: 저장 성공 후 조회 실패') }
      if (scenario === 'error' || scenario === 'moreError' && cursor) fail(500, 'INTERNAL_ERROR', '개발 예시: 조회 실패')
      const filtered = scenario === 'empty' ? [] : example.rows.filter(row => (!status || row.status === status) && (!cursor || BigInt(row.id) < BigInt(cursor)))
      const items = filtered.slice(0, 4).map(row => ({ ...row, post: { ...row.post } }))
      return validatePage({ items, hasNext: filtered.length > 4, nextCursor: filtered.length > 4 ? String(items.at(-1)!.id) : null }, cursor, status)
    },
    async act(id, action, signal) {
      if (outcome === 'noAuth') return actOnApplication(id, action, { signal })
      await pause(signal, 600)
      if (outcome === 'expired' || viewer === 'guest') fail(401, 'UNAUTHORIZED', '인증 만료')
      if (outcome === 'forbidden') fail(403, 'FORBIDDEN', '권한 없음')
      if (outcome === 'failure') fail(500, 'INTERNAL_ERROR', '개발 예시: 서버 오류입니다. 목록을 다시 조회해 주세요.')
      const item = example.rows.find(row => row.id === id)
      if (!item) fail(404, 'NOT_FOUND', '지원 내역 없음')
      if (outcome === 'changed') { item.status = action === 'cancel' ? 'ACCEPTED' : 'LEFT'; fail(409, 'INVALID_APPLICATION_STATUS', '상태 변경') }
      if (item.status !== (action === 'cancel' ? 'PENDING' : 'ACCEPTED')) fail(409, 'INVALID_APPLICATION_STATUS', '상태 변경')
      item.status = action === 'cancel' ? 'CANCELED' : 'LEFT'; example.processed = true
      item.decidedAt = action === 'cancel' ? new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Seoul' }).replace(' ', 'T') : item.decidedAt
      if (outcome === 'network') throw new TypeError('예시: 서버 처리는 됐지만 응답을 받지 못함')
      return { applicationId: id, status: item.status as 'CANCELED' | 'LEFT', ...(action === 'leave' ? { roleReopened: false, postReopened: false } : {}) }
    },
  }
}
