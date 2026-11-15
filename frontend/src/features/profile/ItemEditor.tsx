import { Button } from '../../components/Button'
import { Input } from '../../components/Input'
import type { ItemInput } from './api'
import { experienceLabels, visibilityLabels } from './presentation'

export function ItemEditor({ item, change, remove }: {
  item: ItemInput
  change: (patch: Partial<ItemInput>) => void
  remove: () => void
}) {
  const experience = item.itemType === 'EXPERIENCE'
  const portfolio = item.itemType === 'PORTFOLIO'
  const award = item.itemType === 'AWARD'
  const titleLabel = experience ? '활동명' : portfolio ? '라벨' : award ? '대회명' : '이름'

  return <fieldset className="profile-item">
    <legend>{item.title || '새 항목'}</legend>
    <div className="profile-heading profile-item-controls">
      <div className="profile-pills" role="group" aria-label="공개 범위">
        <button type="button" aria-pressed={item.visibility === 'PUBLIC'} onClick={() => change({ visibility: 'PUBLIC' })}>{visibilityLabels.PUBLIC}</button>
        <button type="button" aria-pressed={item.visibility === 'APPLIED_LEADER'} onClick={() => change({ visibility: 'APPLIED_LEADER' })}>{visibilityLabels.APPLIED_LEADER}</button>
      </div>
      <Button className="profile-secondary" onClick={remove}>삭제</Button>
    </div>
    {experience && <div className="profile-pills" role="group" aria-label="활동 유형">
      {Object.entries(experienceLabels).map(([value, label]) =>
        <button key={value} type="button" aria-pressed={item.subType === value}
          onClick={() => change({ subType: value })}>{label}</button>)}
    </div>}
    <div className="profile-fields">
      <Input label={titleLabel} value={item.title} required maxLength={100} onChange={event => change({ title: event.target.value })} />
      {portfolio && <Input label="URL (http 또는 https)" type="url" value={item.url ?? ''} required maxLength={500}
        onChange={event => change({ url: event.target.value })} />}
      {experience && <Input label="역할" value={item.role ?? ''} maxLength={50} onChange={event => change({ role: event.target.value })} />}
      {!portfolio && !experience && <Input label={award ? '수상 내역' : '발급 기관'}
        value={item.issuer ?? ''} maxLength={50} required onChange={event => change({ issuer: event.target.value })} />}
      {!portfolio && <Input label={experience ? '시작일' : award ? '수상일' : '취득일'} type="date"
        value={item.startDate ?? ''} required onChange={event => change({ startDate: event.target.value || null })} />}
      {experience && <Input label="종료일 (비우면 진행 중)" type="date"
        min={item.startDate ?? undefined} value={item.endDate ?? ''}
        onChange={event => change({ endDate: event.target.value || null })} />}
      {experience && <label className="profile-wide">설명 (300자)
        <textarea value={item.description ?? ''} maxLength={300} onChange={event => change({ description: event.target.value })} />
      </label>}
    </div>
  </fieldset>
}
