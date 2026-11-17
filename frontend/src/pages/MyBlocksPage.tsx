import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../components/Button'
import { useSafetyToken, safetyTokenCurrent } from '../features/safety/auth'
import { createBlocksController, initialBlocks } from '../features/safety/blocks'
import '../features/safety/safety.css'

export default function MyBlocksPage() {
  const token = useSafetyToken()
  return <section className="safety-blocks"><h1>내 차단 목록</h1><p>차단을 해제해도 차단으로 마감된 지원은 복원되지 않아요.</p>
    {token ? <Blocks key={token} token={token} /> : <p><Link to="/login">로그인</Link> 후 차단 목록을 확인할 수 있어요.</p>}
  </section>
}
function Blocks({ token }: { token: string }) {
  const [state, setState] = useState(initialBlocks), [confirm, setConfirm] = useState<number | null>(null)
  const controller = useRef<ReturnType<typeof createBlocksController> | null>(null)
  useEffect(() => {
    const value = createBlocksController(token, setState, () => safetyTokenCurrent(token)); controller.current = value
    void value.load(); return () => value.dispose()
  }, [token])
  const busy = state.loading || state.removing !== null
  return <>
    {state.error && <p role="alert">{state.error}</p>}{state.notice && <p role="status">{state.notice}</p>}
    {state.denied ? <Link to="/login">다시 로그인</Link> : <>
      <Button className="safety-secondary" disabled={busy} onClick={() => { setConfirm(null); void controller.current?.load() }}>목록 새로고침</Button>
      <ul aria-label="차단한 사용자">{state.items.map(row => <li key={row.userId}><div><strong>{row.nickname ?? '이름 미등록'}</strong><p>{row.schoolName ?? '학교 미등록'}</p><time dateTime={row.blockedAt}>{row.blockedAt.replace('T', ' ')}</time></div>
        {confirm === row.userId ? <div><p>{row.nickname ?? '이 사용자'}님의 차단을 해제할까요?</p><div className="safety-actions"><Button className="safety-secondary" disabled={busy} onClick={() => setConfirm(null)}>취소</Button><Button disabled={busy} onClick={() => { void controller.current?.remove(row.userId); setConfirm(null) }}>해제 확인</Button></div></div> : <Button className="safety-secondary" disabled={busy} onClick={() => setConfirm(row.userId)}>차단 해제</Button>}
      </li>)}</ul>
      {!busy && !state.error && !state.items.length && <p>차단한 사용자가 없어요.</p>}
      {state.hasNext && <Button disabled={busy} onClick={() => void controller.current?.load(true)}>더 보기</Button>}
    </>}
    {busy && <p role="status">{state.loading ? '목록을 불러오는 중…' : '차단을 해제하는 중…'}</p>}
  </>
}
