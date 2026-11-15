import { applyDecision, decisionBlock, managementFailure, findApplication } from './model'
import type { Decision, DecisionResult, Reason, Snapshot } from './model'

export type ManagementService = {
  load: (signal: AbortSignal) => Promise<Snapshot>;
  decide: (id: number, decision: Decision, reason: Reason | null) => Promise<DecisionResult>
}
export type ManagementState = {
  snapshot: Snapshot | null; loading: boolean; busy: boolean; needsRefresh: boolean;
  error: ReturnType<typeof managementFailure> | null; notice: string; revision: number
}
export const initialManagementState = (): ManagementState => ({ snapshot: null, loading: false, busy: false, needsRefresh: false, error: null, notice: '', revision: 0 })
export function createManagementController(service: ManagementService, change: (state: ManagementState) => void) {
  let state = initialManagementState(), disposed = false, mutation = false, sequence = 0
  let request: AbortController | null = null
  const confirmed = new Set<number>()
  function update(patch: Partial<ManagementState>) { if (!disposed) { state = { ...state, ...patch }; change(state) } }
  async function load(afterDecision = false) {
    if (disposed || mutation && !afterDecision) return
    request?.abort()
    const controller = new AbortController(); request = controller
    const generation = ++sequence
    update({ loading: true, error: null })
    try {
      const snapshot = await service.load(controller.signal)
      if (disposed || generation !== sequence) return
      // 이미 성공 확인한 지원을 지연된 조회의 PENDING으로 되돌리지 않습니다.
      if ([...confirmed].some(id => findApplication(snapshot, id)?.application.status === 'PENDING')) {
        throw new Error('처리 결과가 아직 조회에 반영되지 않았습니다.')
      }
      update({ snapshot, needsRefresh: false, error: null, revision: state.revision + 1 })
    } catch (error) {
      if (disposed || generation !== sequence) return
      const failure = managementFailure(error)
      update({ error: failure, needsRefresh: !!state.snapshot, ...(failure.access ? { snapshot: null } : {}) })
    } finally { if (generation === sequence) update({ loading: false }) }
  }
  return {
    load,
    async decide(id: number, decision: Decision, reason: Reason | null) {
      if (disposed || mutation || state.loading || state.needsRefresh || !state.snapshot) return
      const block = decisionBlock(state.snapshot, id, decision)
      if (block) { update({ error: { code: 'LOCAL_BLOCK', message: block, access: false, reconcile: false } }); return }
      mutation = true
      update({ busy: true, error: null, notice: '' })
      let result: DecisionResult
      try { result = await service.decide(id, decision, reason) }
      catch (error) {
        const failure = managementFailure(error)
        update({ error: failure, needsRefresh: failure.reconcile || failure.access, ...(failure.access ? { snapshot: null } : {}) })
        mutation = false; update({ busy: false }); return
      }
      if (disposed) { mutation = false; return }
      confirmed.add(id)
      const snapshot = applyDecision(state.snapshot!, result)
      // 역할이 마감되면서 자동 종료된 대기 지원도 다시 처리할 수 없게 보존합니다.
      snapshot.roles.flatMap(role => role.applications).filter(item => item.status === 'CLOSED').forEach(item => confirmed.add(item.id))
      update({ snapshot, needsRefresh: true, notice: `${decision === 'accept' ? '수락' : '거절'} 처리가 완료됐어요.${result.roleClosed ? ' 정원이 차서 해당 역할의 남은 대기 지원이 마감됐어요.' : ''}${result.postClosed ? ' 모든 역할 모집이 마감됐어요.' : ''}`, revision: state.revision + 1 })
      await load(true)
      mutation = false; update({ busy: false })
    },
    dispose() { disposed = true; sequence++; request?.abort() },
  }
}
