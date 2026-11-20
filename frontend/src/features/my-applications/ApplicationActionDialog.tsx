import { useEffect, useRef } from 'react'
import { Button } from '../../components/Button'
import { applicationTitle, type Action, type MyApplication } from './model'

export function ApplicationActionDialog({ item, action, onClose, onConfirm }: { item: MyApplication; action: Action; onClose: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = dialog.current!, trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element.showModal(); element.querySelector<HTMLButtonElement>('button')?.focus()
    return () => { element.close(); document.body.style.overflow = overflow; if (trigger?.isConnected) trigger.focus() }
  }, [])
  return <dialog className="my-application-dialog" ref={dialog} aria-labelledby="my-application-action-title" aria-describedby="my-application-action-description" onCancel={event => { event.preventDefault(); onClose() }}>
    <h2 id="my-application-action-title">{action === 'cancel' ? '지원을 취소할까요?' : '팀에서 나갈까요?'}</h2>
    <p id="my-application-action-description"><strong>{applicationTitle(item)}</strong> · {item.roleName}</p>
    <p>{action === 'cancel' ? '지원 취소 후에는 같은 모집글에 다시 지원할 수 없습니다.' : '팀에서 나가면 연락 수단을 더 이상 확인할 수 없습니다. 같은 모집글에 다시 지원할 수 없습니다.'}</p>
    <div><Button className="my-application-soft" onClick={onClose}>돌아가기</Button><Button onClick={onConfirm}>{action === 'cancel' ? '지원 취소 확인' : '팀 나가기 확인'}</Button></div>
  </dialog>
}
