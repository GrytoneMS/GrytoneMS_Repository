import { applyToPost } from './application-api'
import { applicationBlock, applicationFailure, validateApplication } from './application-model'
import type { ApplicationDraft, ApplicationErrors, ApplyResponse } from './application-model'
import type { PostDetail } from './model'

export type ApplicationSender = (postId: number, draft: ApplicationDraft) => Promise<ApplyResponse>
export async function refreshAppliedPost(current: PostDetail, load: () => Promise<PostDetail>) {
  try {
    const updated = await load()
    return { post: { ...updated, myApplication: updated.myApplication ?? current.myApplication }, refreshFailed: false }
  } catch {
    return { post: current, refreshFailed: true }
  }
}
type Callbacks = {
  invalid: (errors: ApplicationErrors) => void; start: () => void;
  success: (result: ApplyResponse, draft: ApplicationDraft) => void;
  failure: (error: ReturnType<typeof applicationFailure>) => void; settled: () => void
}
export function createApplicationSubmitter() {
  let active = false
  return async (post: PostDetail, draft: ApplicationDraft, callbacks: Callbacks, send: ApplicationSender = applyToPost) => {
    if (active) return
    const errors = validateApplication(draft, post)
    if (Object.keys(errors).length) { callbacks.invalid(errors); return }
    const blocked = applicationBlock(post)
    if (blocked) { callbacks.failure({ message: blocked, fields: {} }); return }
    active = true
    callbacks.start()
    let result: ApplyResponse
    try { result = await send(post.id, { ...draft }) }
    catch (error) { callbacks.failure(applicationFailure(error)); active = false; callbacks.settled(); return }
    // 상세 재조회는 호출자가 별도로 처리합니다. 재조회 실패를 지원 실패로 바꾸지 않습니다.
    try { callbacks.success(result, draft) }
    finally { active = false; callbacks.settled() }
  }
}
