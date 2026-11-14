import { ApiError } from '../../api/client'
import type { PostDetail } from './model'
import { safeContactLink } from './post-actions-api'

export const canReadContact = (post: PostDetail) => post.isMine || post.myApplication?.status === 'ACCEPTED'
export const canClosePost = (post: PostDetail) => post.isMine && post.status === 'OPEN'
export function closedPost(post: PostDetail): PostDetail {
  return { ...post, status: 'CLOSED', roles: post.roles.map(role => ({ ...role, status: 'CLOSED' })),
    myApplication: post.myApplication?.status === 'PENDING' ? { ...post.myApplication, status: 'CLOSED' } : post.myApplication }
}
export function actionError(error: unknown) {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      AUTH_PENDING: '공통 인증 연동 대기 중이에요. 인증 연동 전에는 요청을 보내지 않습니다.',
      UNAUTHORIZED: '로그인이 만료됐어요. 다시 로그인한 뒤 확인해 주세요.',
      FORBIDDEN: '현재 이 작업을 할 권한이 없어요. 최신 상세 정보를 확인해 주세요.',
      NOT_FOUND: '삭제되었거나 접근할 수 없는 모집글이에요.',
      POST_DELETED: '삭제된 모집글이에요.',
      POST_ALREADY_CLOSED: '이미 마감된 모집글이에요. 최신 모집 현황을 확인해 주세요.',
      SUSPENDED: '이용이 정지된 계정이에요.',
      UNVERIFIED: '학교 인증 상태를 확인해 주세요.',
      INVALID_RESPONSE: '서버 응답을 확인할 수 없어요. 최신 정보를 다시 확인해 주세요.',
    }
    return messages[error.code] ?? (error.status === 401 ? messages.UNAUTHORIZED : error.status === 403 ? messages.FORBIDDEN : error.status === 404 ? messages.NOT_FOUND : '요청을 완료하지 못했어요. 잠시 후 다시 확인해 주세요.')
  }
  return '서버와 통신하지 못했어요. 처리 결과가 불확실하므로 최신 정보를 먼저 확인해 주세요.'
}

export type PostActionsState = {
  post: PostDetail; closing: boolean; refreshing: boolean; closeConfirmed: boolean;
  needsRefresh: boolean; closeError: string; closeNotice: string;
  contact: string | null; contactLoading: boolean; contactError: string; copyNotice: string; copying: boolean;
}
type Dependencies = {
  close: (signal: AbortSignal) => Promise<void>; contact: (signal: AbortSignal) => Promise<string>;
  load: (signal: AbortSignal) => Promise<PostDetail>; onPost: (post: PostDetail) => void;
}

/** 1 controller = 1 게시글·사용자·인증 세션. dispose는 비동기 결과를 모두 폐기합니다. */
export function createPostActions(initial: PostDetail, dependencies: Dependencies) {
  let state: PostActionsState = { post: initial, closing: false, refreshing: false, closeConfirmed: false, needsRefresh: false,
    closeError: '', closeNotice: '', contact: null, contactLoading: false, contactError: '', copyNotice: '', copying: false }
  let alive = true
  let contactVersion = 0
  let contactRequest: AbortController | undefined
  const requests = new Set<AbortController>()
  const listeners = new Set<() => void>()
  const update = (patch: Partial<PostActionsState>) => { if (alive) { state = { ...state, ...patch }; listeners.forEach(listener => listener()) } }
  const clearContact = () => {
    contactVersion++; contactRequest?.abort()
    update({ contact: null, contactLoading: false, contactError: '', copyNotice: '', copying: false })
  }
  const publishPost = (post: PostDetail) => { update({ post }); dependencies.onPost(post) }
  async function refresh() {
    if (!alive || state.refreshing || state.closing) return
    update({ refreshing: true, needsRefresh: true })
    const request = new AbortController(); requests.add(request)
    try {
      const loaded = await dependencies.load(request.signal)
      if (!alive || request.signal.aborted) return
      // 확인된 마감은 지연된/익명 상세 응답으로 다시 OPEN이 되지 않습니다.
      const post = state.closeConfirmed ? closedPost(loaded) : loaded
      if (post.id !== state.post.id) throw new Error('Unexpected post')
      if (!canReadContact(post)) clearContact()
      publishPost(post)
      update({ needsRefresh: false })
    } catch { if (alive && !request.signal.aborted) update({ needsRefresh: true }) }
    finally { requests.delete(request); if (alive) update({ refreshing: false }) }
  }
  return {
    getSnapshot: () => state,
    activate() { alive = true },
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    updatePost(post: PostDetail) {
      if (!alive) return
      if (!canReadContact(post)) clearContact()
      update({ post: state.closeConfirmed ? closedPost(post) : post })
    },
    refresh,
    async close() {
      if (!alive || state.closing || state.refreshing || state.needsRefresh || !canClosePost(state.post)) return
      update({ closing: true, closeError: '', closeNotice: '' })
      const request = new AbortController(); requests.add(request)
      try {
        await dependencies.close(request.signal)
        if (!alive || request.signal.aborted) return
        update({ closeConfirmed: true, closeNotice: '모집을 마감했어요. 대기 지원은 마감되고 대기자에게 알림이 발송됩니다.' })
        publishPost(closedPost(state.post))
      } catch (error) {
        if (!alive || request.signal.aborted) return
        if (error instanceof ApiError && error.code === 'POST_ALREADY_CLOSED') {
          update({ closeConfirmed: true }); publishPost(closedPost(state.post))
        }
        clearContact()
        update({ closeError: actionError(error), needsRefresh: !(error instanceof ApiError && error.code === 'AUTH_PENDING') })
      } finally { requests.delete(request); if (alive) update({ closing: false }) }
      if (alive && (state.closeConfirmed || state.needsRefresh)) await refresh()
    },
    async readContact() {
      if (!alive || state.contactLoading || !canReadContact(state.post)) return
      clearContact()
      const version = contactVersion
      const request = new AbortController(); contactRequest = request; requests.add(request)
      update({ contactLoading: true })
      try {
        const value = await dependencies.contact(request.signal)
        if (!alive || request.signal.aborted || version !== contactVersion || !canReadContact(state.post)) return
        const link = safeContactLink(value)
        if (!link) throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '' })
        update({ contact: link })
      } catch (error) {
        if (alive && !request.signal.aborted && version === contactVersion) update({ contact: null, contactError: actionError(error) })
      } finally { requests.delete(request); if (alive && version === contactVersion) update({ contactLoading: false }) }
    },
    clearContact,
    async copy(write: (text: string) => Promise<void>) {
      if (!alive || !state.contact || state.copying || !canReadContact(state.post)) return
      const version = contactVersion
      update({ copying: true, copyNotice: '' })
      try { await write(state.contact); if (alive && version === contactVersion) update({ copyNotice: '연락 수단을 복사했어요.' }) }
      catch { if (alive && version === contactVersion) update({ copyNotice: '복사하지 못했어요. 표시된 주소를 직접 선택해 복사해 주세요.' }) }
      finally { if (alive && version === contactVersion) update({ copying: false }) }
    },
    dispose() {
      clearContact(); alive = false; requests.forEach(request => request.abort()); requests.clear(); listeners.clear()
      state = { ...state, contact: null, contactLoading: false, copyNotice: '' }
    },
  }
}
