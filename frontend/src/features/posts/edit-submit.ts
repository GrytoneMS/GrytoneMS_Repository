import type { PostDraft, CreateErrors } from './create-model'
import { editFailure, validateEditDraft, type EditSnapshot } from './edit-model'

type Callbacks = { invalid: (errors: CreateErrors) => void; start: () => void; success: () => void; failure: (result: ReturnType<typeof editFailure>) => void; settled: () => void }
/** 같은 틱의 중복 제출을 막고, 화면을 떠난 뒤 도착한 결과는 무시합니다. */
export function createEditSubmitter(snapshot: EditSnapshot, save: (draft: PostDraft, signal: AbortSignal) => Promise<unknown>) {
  let active: AbortController | null = null
  return {
    async submit(draft: PostDraft, callbacks: Callbacks) {
      if (active) return
      const errors = validateEditDraft(draft, snapshot)
      if (Object.keys(errors).length) { callbacks.invalid(errors); return }
      const controller = new AbortController(); active = controller
      callbacks.start()
      try {
        await save(draft, controller.signal)
        if (!controller.signal.aborted) callbacks.success()
      } catch (error) {
        if (!controller.signal.aborted) callbacks.failure(editFailure(error))
      } finally {
        if (active === controller) active = null
        if (!controller.signal.aborted) callbacks.settled()
      }
    },
    cancel() { active?.abort(); active = null },
  }
}
