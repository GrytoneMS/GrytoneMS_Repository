import { createPost } from './create-api'
import { createFailure, toCreatePostRequest, validatePostDraft } from './create-model'
import type { CreateErrors, PostDraft } from './create-model'
import type { PostRequestOptions } from './api'

type SubmitCallbacks = {
  onInvalid: (errors: CreateErrors) => void; onStart: () => void; onSuccess: (id: number) => void;
  onFailure: (failure: ReturnType<typeof createFailure>) => void; onSettled: () => void
}
/** React 재렌더 전의 중복 제출도 막고, 화면 이탈 후에는 결과를 반영하지 않습니다. */
export function createPostSubmitter() {
  let active: AbortController | null = null
  return {
    async submit(draft: PostDraft, callbacks: SubmitCallbacks, options: Pick<PostRequestOptions, 'token'> = {}) {
      if (active) return
      const errors = validatePostDraft(draft)
      if (Object.keys(errors).length) { callbacks.onInvalid(errors); return }
      const controller = new AbortController(); active = controller
      callbacks.onStart()
      try {
        // 인증 컨텍스트가 아직 없습니다. 임시 사용자 헤더나 토큰 저장 정책을 만들지 않습니다.
        const result = await createPost(toCreatePostRequest(draft), { token: options.token, signal: controller.signal })
        if (!controller.signal.aborted) callbacks.onSuccess(result.id)
      } catch (error) {
        if (!controller.signal.aborted) callbacks.onFailure(createFailure(error))
      } finally {
        if (!controller.signal.aborted) callbacks.onSettled()
        if (active === controller) active = null
      }
    },
    cancel() { active?.abort(); active = null },
  }
}
