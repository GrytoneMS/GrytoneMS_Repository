import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { Button } from '../../components/Button'
import { BrandLogo } from '../../components/BrandLogo'
import { useAuth } from '../auth/useAuth'
import { AuthStatus } from '../auth/AuthContext'
import type { AdminService } from './api'
import { authenticatedAdmin } from './source'
import { createAdminController, type AdminController, type AdminState } from './controller'
import { adminExampleEnabled, createMockAdmin } from './mock'
import { actions, allowedActions, jobError, menus, reasons, type Job, type Menu, type Report, type School, type SchoolInput, type Verification } from './model'
import './admin.css'

const blankSchool: SchoolInput = { name: '', campus: '', emailDomain: '', region: 'CHEONAN' }
const when = (date: string | null) => date ? date.replace('T', ' ').replace(/\.\d+Z?$/, '').slice(0, 19) : '—'
const typeLabels = { POST: '모집글', USER: '사용자', APPLICATION: '지원 메시지' }
export default function AdminPage() {
  const { section = 'reports' } = useParams()
  const [params, setParams] = useSearchParams()
  const menu: Menu = Object.hasOwn(menus, section) ? section as Menu : 'reports'
  const auth = useAuth()
  const example = adminExampleEnabled(params)
  const exampleQuery = new URLSearchParams([...params].filter(([k]) => k.startsWith('admin'))).toString()
  const service = useMemo<AdminService>(() => example ? createMockAdmin(new URLSearchParams(exampleQuery)) : authenticatedAdmin(auth.store, auth.session), [auth.session, auth.store, example, exampleQuery])
  const status = menu === 'verifications' ? (['PENDING', 'APPROVED', 'REJECTED'].includes(params.get('status') ?? '') ? params.get('status')! : 'PENDING') : menu === 'reports' ? (params.get('status') === 'RESOLVED' ? 'RESOLVED' : 'RECEIVED') : ''
  return <div className="admin-app">
    <aside className="admin-sidebar"><Link to="/posts" className="admin-brand"><BrandLogo /></Link><p>관리자</p>
      <nav aria-label="관리자 메뉴">{Object.entries(menus).map(([key, label]) => <Link key={key} aria-current={key === menu ? 'page' : undefined} to={`/admin/${key}${example ? `?${exampleQuery}` : ''}`}>{label}</Link>)}</nav><Link className="admin-home" to="/posts">사용자 화면으로 →</Link>
    </aside>
    <main className="admin-main" id="admin-content"><h1>{menus[menu]}</h1>
      {example && <aside className="admin-example"><strong>개발용 관리자 예시 · 실제 권한/사용자/DB 변경 없음</strong>
        <label>사용자<select value={params.get('adminViewer') ?? 'admin'} onChange={e => setParams(p => { const n = new URLSearchParams(p); n.set('adminViewer', e.target.value); return n })}>{Object.entries({ admin: '예시 관리자', user: '일반 사용자', guest: '비로그인' }).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label>결과<select value={params.get('adminResult') ?? 'success'} onChange={e => setParams(p => { const n = new URLSearchParams(p); n.set('adminResult', e.target.value); return n })}>{Object.entries({ success: '정상', failure: '처리 실패', lost: '처리 후 응답 유실', refreshFailure: '성공 후 조회 실패', loadFailure: '목록 실패', expired: '인증 만료', forbidden: '권한 없음' }).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <Button onClick={() => setParams(p => { const n = new URLSearchParams(p); n.set('adminReset', String(Date.now())); return n })}>예시 초기화</Button>
      </aside>}
      {!example && auth.status !== 'authenticated' ? <AuthStatus /> : <AdminContent key={`${auth.scope}:${exampleQuery}:${menu}:${status}`} service={service} menu={menu} status={status} current={() => example || auth.store.getSnapshot().session === auth.session} onStatus={value => setParams(p => { const n = new URLSearchParams(p); n.set('status', value); return n })} />}
    </main>
  </div>
}
function AdminContent({ service, menu, status, current, onStatus }: { service: AdminService; menu: Menu; status: string; current: () => boolean; onStatus: (status: string) => void }) {
  const [controller] = useState(() => createAdminController(service, menu, status, current))
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot, controller.getSnapshot)
  const [job, setJob] = useState<Job | null>(null)
  useEffect(() => { controller.resume(); void controller.start(); return controller.dispose }, [controller])
  if (state.access !== 'ready') return <section className="admin-panel admin-gate" role="status">
    <h2>{state.access === 'checking' ? '관리자 권한 확인 중…' : state.access === 'anonymous' ? '로그인이 필요합니다' : state.access === 'denied' ? '접근할 수 없습니다' : '권한 확인 실패'}</h2>
    {state.error && <p>{state.error}</p>}{state.access === 'anonymous' && <Link to="/login">기존 로그인 화면으로</Link>}{state.access !== 'checking' && <Button onClick={() => void controller.start()}>권한 다시 확인</Button>}
  </section>
  const locked = state.busy || state.loading || state.blocked
  return <>
    <div className="admin-toolbar"><p>{state.account?.nickname ?? '관리자'} · 관리자 권한 확인됨</p><Button disabled={state.busy || state.loading} onClick={() => void controller.refresh()}>목록 새로고침</Button></div>
    {(menu === 'reports' || menu === 'verifications') && <div className="admin-tabs" role="group" aria-label="처리 상태">{Object.entries(menu === 'reports' ? { RECEIVED: '접수', RESOLVED: '처리완료' } : { PENDING: '대기', APPROVED: '승인', REJECTED: '반려' }).map(([v, l]) => <Button key={v} aria-pressed={status === v} disabled={state.busy} onClick={() => onStatus(v)}>{l}</Button>)}</div>}
    {state.notice && <p className="admin-success" role="status">{state.notice}</p>}
    {state.error && <div className="admin-alert" role="alert"><p>{state.error}</p><Button disabled={state.busy || state.loading} onClick={() => void controller.refresh()}>조회만 다시 시도</Button></div>}
    {state.blocked && <p className="admin-note">최신 상태를 조회하기 전에는 처리를 잠급니다. 이미 처리됐을 수 있으니 같은 요청을 반복하지 마세요.</p>}
    {state.loading && <p role="status">목록을 불러오는 중…</p>}
    {menu === 'reports' && <Reports rows={state.items as Report[]} locked={locked} onJob={setJob} />}
    {menu === 'verifications' && <Verifications rows={state.items as Verification[]} locked={locked} controller={controller} state={state} onJob={setJob} />}
    {menu === 'suspensions' && <Suspensions rows={state.items as Report[]} locked={locked} onJob={setJob} />}
    {menu === 'schools' && <Schools rows={state.items as School[]} locked={locked} onJob={setJob} />}
    {state.loaded && !state.loading && !state.items.length && <p className="admin-empty">표시할 항목이 없습니다.</p>}
    {state.moreError && <p className="admin-alert" role="alert">{state.moreError}<Button disabled={state.loading || state.busy} onClick={() => void controller.more()}>더 보기 재시도</Button></p>}
    {state.hasNext && <Button disabled={state.loading || state.busy || state.blocked} onClick={() => void controller.more()}>더 보기</Button>}
    {job && <JobDialog key={`${job.kind}:${job.id}`} initial={job} state={state} controller={controller} onClose={() => setJob(null)} />}
  </>
}
export function Reports({ rows, locked, onJob }: { rows: Report[]; locked: boolean; onJob: (job: Job) => void }) {
  return <><p className="admin-note">접수 신고는 48시간 이내 처리를 기준으로 확인해 주세요. 같은 대상의 접수 신고는 함께 처리됩니다. 신고 수는 현재 미처리 건수입니다.</p>
    <div className="admin-table-wrap" tabIndex={0} role="region" aria-label="신고 목록"><table><thead><tr><th>접수 시간</th><th>대상·내용</th><th>사유</th><th>미처리 신고</th><th>상태</th><th>조치</th></tr></thead><tbody>{rows.map(r => <tr key={r.id}>
      <td>{when(r.createdAt)}</td><td><strong>{typeLabels[r.targetType]} · {r.target?.title ?? '대상 없음'}</strong><p>대상 #{r.targetId}</p><details><summary>신고 내용 확인</summary><p className="admin-content-text">{r.target?.content ?? '확인할 대상 내용이 없습니다.'}</p><p>신고 상세: {r.detail || '없음'}</p><p>신고자: {r.reporter.nickname ?? '이름 없음'} · #{r.reporter.id}</p>{r.target?.hidden && <p>숨김 상태</p>}{r.target?.deleted && <p>삭제된 대상</p>}</details></td>
      <td>{reasons[r.reasonCode] ?? r.reasonCode}</td><td>{r.pendingCountForTarget}</td><td><span className="admin-chip">{r.status === 'RECEIVED' ? '접수' : '처리완료'}</span>{r.action && <p>{actions[r.action]} · {when(r.handledAt)}</p>}</td><td><div className="admin-row-actions">{allowedActions(r).map(action => <Button key={action} className={action === 'SUSPEND' ? 'admin-danger' : ''} disabled={locked} onClick={() => onJob({ kind: 'resolve', id: r.id, action, days: 7, reason: '' })}>{actions[action]}</Button>)}</div></td>
    </tr>)}</tbody></table></div></>
}
export function Verifications({ rows, locked, controller, state, onJob }: { rows: Verification[]; locked: boolean; controller: AdminController; state: AdminState; onJob: (job: Job) => void }) {
  return <><p className="admin-note">대기는 오래된 신청부터 표시합니다. 처리 후 화면에서 서류를 제거하며, 서버의 파일 삭제 완료를 프론트에서 단정하지 않습니다.</p>
    <div className="admin-table-wrap" tabIndex={0} role="region" aria-label="서류 심사 목록"><table><thead><tr><th>신청자</th><th>학교</th><th>신청 시간</th><th>상태·사유</th><th>심사</th></tr></thead><tbody>{rows.map(v => <tr key={v.id}>
      <td>{v.user.nickname ?? '이름 없음'}<p>{v.user.email ?? '이메일 정보 없음'}</p></td><td>{v.school.name} {v.school.campus}</td><td>{when(v.requestedAt)}</td><td>{({ PENDING: '대기', APPROVED: '승인', REJECTED: '반려' })[v.status]}<p>{v.rejectReason}</p><p>{when(v.reviewedAt)}</p></td><td>{v.status === 'PENDING' && <div className="admin-row-actions"><Button disabled={locked || state.documentLoading || !v.documentUrl} onClick={() => void controller.showDocument(v.id)}>서류 확인</Button><Button disabled={locked} onClick={() => onJob({ kind: 'approve', id: v.id, reason: '' })}>승인</Button><Button disabled={locked} onClick={() => onJob({ kind: 'reject', id: v.id, reason: '' })}>반려</Button></div>}</td>
    </tr>)}</tbody></table></div>
    {state.documentLoading && <p role="status">심사 서류를 불러오는 중…</p>}
    {state.document && <DocumentPreview blob={state.document} id={state.documentId!} onClose={controller.clearDocument} />}
  </>
}
function DocumentPreview({ blob, id, onClose }: { blob: Blob; id: number; onClose: () => void }) {
  const image = useRef<HTMLImageElement>(null)
  useEffect(() => { const next = URL.createObjectURL(blob), element = image.current!; element.src = next; return () => { element.removeAttribute('src'); URL.revokeObjectURL(next) } }, [blob])
  return <section className="admin-panel admin-document" aria-label="심사용 서류"><div><h2>신청 #{id} · 심사용 서류</h2><Button onClick={onClose}>서류 닫기</Button></div><p>개인정보가 포함될 수 있습니다. 심사 용도로만 확인해 주세요.</p><img ref={image} alt={`신청 ${id} 심사용 서류`} /></section>
}
function Suspensions({ rows, locked, onJob }: { rows: Report[]; locked: boolean; onJob: (job: Job) => void }) {
  const [selected, setSelected] = useState('')
  const targets = rows.filter((r, i) => r.target && rows.findIndex(other => other.target?.authorId === r.target?.authorId) === i)
  const target = targets.find(r => String(r.id) === selected)
  return <section className="admin-panel"><h2>이용 정지 대상 선택</h2><p className="admin-note">관리자 사용자 검색·정지 목록 API가 없어 조회한 신고 대상의 작성자만 선택할 수 있습니다. 더 보기로 신고 대상을 추가로 불러올 수 있습니다. 신고 자체를 처리하려면 신고 처리 메뉴를 이용해 주세요.</p>
    <label>신고 대상 작성자<select value={selected} disabled={locked} onChange={e => setSelected(e.target.value)}><option value="">대상을 선택해 주세요</option>{targets.map(r => <option key={r.id} value={r.id}>사용자 #{r.target!.authorId} · {typeLabels[r.targetType]} {r.target?.title} · 신고 #{r.id}</option>)}</select></label>
    <p>7일·30일·영구 정지 및 사용자에게 안내될 사유를 확인창에서 입력합니다. 관리자는 정지할 수 없으며 서버에서 최종 판단합니다.</p>
    <Button className="admin-danger" disabled={locked || !target} onClick={() => target && onJob({ kind: 'suspend', id: target.target!.authorId, reportId: target.id, days: 7, reason: '' })}>정지 내용 입력</Button>
  </section>
}
export function Schools({ rows, locked, onJob }: { rows: School[]; locked: boolean; onJob: (job: Job) => void }) {
  return <><div className="admin-toolbar"><p className="admin-note">현재 API는 활성 학교 목록만 제공합니다. 비활성 학교 전체 조회·검색은 API 추가가 필요합니다. 기존 인증 회원 정보는 유지됩니다.</p><Button disabled={locked} onClick={() => onJob({ kind: 'createSchool', id: 0, school: { ...blankSchool } })}>학교 추가</Button></div>
    <div className="admin-table-wrap" tabIndex={0} role="region" aria-label="학교 목록"><table><thead><tr><th>학교명</th><th>캠퍼스</th><th>이메일 도메인</th><th>지역</th><th>상태</th><th>관리</th></tr></thead><tbody>{rows.map(s => <tr key={s.id}><td>{s.name}</td><td>{s.campus || '—'}</td><td>{s.emailDomain}</td><td>{s.region === 'CHEONAN' ? '천안' : '아산'}</td><td>활성</td><td><div className="admin-row-actions"><Button disabled={locked} onClick={() => onJob({ kind: 'updateSchool', id: s.id, school: { name: s.name, campus: s.campus ?? '', emailDomain: s.emailDomain, region: s.region } })}>수정</Button><Button disabled={locked} onClick={() => onJob({ kind: 'deactivateSchool', id: s.id })}>비활성화</Button></div></td></tr>)}</tbody></table></div></>
}
export function JobDialog({ initial, state, controller, onClose }: { initial: Job; state: AdminState; controller: AdminController; onClose: () => void }) {
  const [job, setJob] = useState(initial)
  const ref = useRef<HTMLDialogElement>(null)
  const busy = state.busy || state.loading
  const label = job.kind === 'resolve' ? actions[job.action] : ({ approve: '서류 승인', reject: '서류 반려', suspend: '이용 정지', createSchool: '학교 등록', updateSchool: '학교 수정', deactivateSchool: '학교 비활성화' })[job.kind]
  const suspension = job.kind === 'suspend' || job.kind === 'resolve' && job.action === 'SUSPEND'
  const target = state.items.find(r => job.kind === 'suspend' ? r.id === job.reportId : r.id === job.id)
  const targetLabel = target && ('targetType' in target ? `${typeLabels[target.targetType]} · ${target.target?.title ?? '대상 없음'}` : 'user' in target ? `${target.user.nickname ?? '이름 없음'} · ${target.school.name}` : target.name)
  useEffect(() => { const d = ref.current!, trigger = document.activeElement; const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; d.showModal(); d.querySelector('button')?.focus(); return () => { d.close(); document.body.style.overflow = overflow; if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus() } }, [])
  return <dialog ref={ref} className="admin-dialog" aria-labelledby="admin-confirm-title" onCancel={e => { e.preventDefault(); if (!busy) onClose() }}><form onSubmit={e => { e.preventDefault(); void controller.submit(job).then(ok => { if (ok) onClose() }) }}>
    <h2 id="admin-confirm-title">{label} 확인</h2><p>{targetLabel}</p><p>대상 #{job.id || '신규 학교'}{job.kind === 'resolve' ? ' · 같은 대상의 접수 신고도 함께 처리됩니다.' : ''}</p>
    {suspension && <SuspensionFields days={(job as Extract<Job, { kind: 'suspend' }>).days} reason={(job as Extract<Job, { kind: 'suspend' }>).reason} disabled={busy} onChange={(days, reason) => setJob({ ...job, days, reason } as Job)} />}
    {job.kind === 'reject' && <label>반려 사유<textarea required maxLength={200} value={job.reason} disabled={busy} onChange={e => setJob({ ...job, reason: e.target.value })} /></label>}
    {(job.kind === 'createSchool' || job.kind === 'updateSchool') && <fieldset disabled={busy}><legend>학교 정보</legend>{(['name', 'campus', 'emailDomain'] as const).map(key => <label key={key}>{{ name: '학교명', campus: '캠퍼스', emailDomain: '이메일 도메인' }[key]}<input required={key !== 'campus'} maxLength={key === 'name' ? 50 : key === 'campus' ? 30 : 100} value={job.school[key]} onChange={e => setJob({ ...job, school: { ...job.school, [key]: e.target.value } })} /></label>)}<label>지역<select value={job.school.region} disabled={job.kind === 'updateSchool'} onChange={e => setJob({ ...job, school: { ...job.school, region: e.target.value as SchoolInput['region'] } })}><option value="CHEONAN">천안</option><option value="ASAN">아산</option></select></label>{job.kind === 'updateSchool' && <p>지역 변경은 현재 수정 API에서 지원하지 않습니다.</p>}</fieldset>}
    {job.kind === 'deactivateSchool' && <p>새 학교 인증 대상에서 제외됩니다. 기존 인증 회원은 유지됩니다.</p>}
    {job.kind === 'resolve' && job.action === 'DELETE' && <p>서버 정책에 따라 모집글 삭제 조치를 수행합니다. 대상을 다시 확인해 주세요.</p>}
    {jobError(job) && <p className="admin-note">{jobError(job)}</p>}
    {state.error && <p className="admin-alert" role="alert">{state.error}</p>}
    {state.blocked && <Button disabled={busy} onClick={() => void controller.refresh()}>조회로 결과 확인</Button>}
    <div className="admin-dialog-actions"><Button disabled={busy} onClick={onClose}>취소</Button><Button type="submit" disabled={busy || state.blocked || !!jobError(job)}>{busy ? '처리 중…' : `${label} 확정`}</Button></div>
  </form></dialog>
}
export function SuspensionFields({ days, reason, disabled, onChange }: { days: number | null; reason: string; disabled: boolean; onChange: (days: number | null, reason: string) => void }) {
  return <fieldset disabled={disabled}><legend>이용 정지 내용</legend><label>기간<select value={days === null ? 'permanent' : String(days)} onChange={e => onChange(e.target.value === 'permanent' ? null : Number(e.target.value), reason)}><option value="7">7일</option><option value="30">30일</option><option value="permanent">영구</option></select></label><label>정지 사유 (사용자에게 안내)<textarea required maxLength={200} value={reason} onChange={e => onChange(days, e.target.value)} /></label></fieldset>
}
