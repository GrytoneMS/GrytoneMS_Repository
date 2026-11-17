import type { AuthStore } from '../auth/session'
import { changeScrap, getMyScraps, realScraps, type ScrapService } from './api'

/** 세션 확인·401 처리·계정 변경 취소를 공통 인증에 맡깁니다. 등록에만 학교 인증이 필요합니다. */
export function authenticatedScraps(store: AuthStore): ScrapService {
  return {
    load: (cursor, signal) => store.execute((session, authSignal) => getMyScraps(cursor, { token: session.token, signal: AbortSignal.any([signal, authSignal]) })),
    change: (id, saved, signal) => store.execute((session, authSignal) => changeScrap(id, saved, { token: session.token, signal: AbortSignal.any([signal, authSignal]) }), saved),
    read: (id, signal) => store.execute((session, authSignal) => realScraps({ token: session.token }).read(id, AbortSignal.any([signal, authSignal]))),
  }
}
