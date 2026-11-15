import { useEffect, useRef } from 'react'
import { Button } from '../../components/Button'
import { reasonLabels, type Decision, type Reason } from './model'

export function DecisionDialog({ name, roleName, decision, reason, onCancel, onConfirm }: {
  name: string; roleName: string; decision: Decision; reason: Reason | null; onCancel: () => void; onConfirm: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const label = decision === 'accept' ? '수락' : '거절'
  useEffect(() => {
    const element = dialog.current!, trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element.showModal(); element.querySelector<HTMLButtonElement>('button')?.focus()
    return () => { element.close(); document.body.style.overflow = overflow; if (trigger?.isConnected) trigger.focus() }
  }, [])
  return <dialog className="applicants-confirm" ref={dialog} aria-labelledby="decision-title" aria-describedby="decision-description"
    onCancel={event => { event.preventDefault(); onCancel() }}>
    <h2 id="decision-title">지원자를 {label}할까요?</h2>
    <p id="decision-description"><strong>{name}</strong>님의 <strong>{roleName}</strong> 지원을 {label}합니다.</p>
    {decision === 'accept' ? <p>정원이 차면 해당 역할의 남은 대기 지원이 자동 마감됩니다.</p> : <p>거절 사유: {reason ? reasonLabels[reason] : '선택하지 않음'}. 거절한 지원은 다시 수락할 수 없습니다.</p>}
    <div><Button className="applicants-soft" onClick={onCancel}>취소</Button><Button onClick={onConfirm}>{label} 확인</Button></div>
  </dialog>
}
