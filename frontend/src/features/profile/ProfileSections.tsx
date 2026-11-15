import type { Profile } from './api'
import { experienceLabels, itemLabels, portfolioLink, visibilityLabels } from './presentation'

export function ProfileSections({ profile, owner = false }: { profile: Profile; owner?: boolean }) {
  return (
    <>
      {(owner || profile.skills.length > 0) && <section className="profile-card">
        <h2>보유 기술·툴</h2>
        {profile.skills.length ? <div className="profile-pills profile-blue-pills">{profile.skills.map(skill => <span key={skill}>{skill}</span>)}</div>
          : <p className="profile-muted">등록한 기술이 없어요.</p>}
      </section>}
      {(owner || profile.activities.length > 0) && <section className="profile-card">
        <div className="profile-heading"><h2>서비스 활동 이력</h2><span className="profile-tag">{owner && !profile.showActivity ? '비공개' : '전체 공개'}</span></div>
        {profile.activities.length ? <ul className="profile-list">{profile.activities.map(activity =>
          <li key={activity.postId}>
            <div className="profile-heading"><strong>{activity.postTitle}</strong><span className="profile-tag">{activity.left ? '중도 하차' : '참여'}</span></div>
            <p className="profile-muted">{activity.roleName} · {activity.periodStart} ~ {activity.periodEnd}</p>
          </li>)}</ul> : <p className="profile-muted">참여 이력이 없어요.</p>}
      </section>}
      {(Object.keys(itemLabels) as Array<keyof typeof itemLabels>).map(type => {
        const items = profile.items.filter(item => item.itemType === type)
        if (!owner && !items.length) return null
        return <section className="profile-card" key={type}>
          <h2>{itemLabels[type]}</h2>
          {items.length ? <ul className="profile-list">{items.map(item => {
            const href = portfolioLink(item.url)
            return <li key={item.id}>
              <div className="profile-heading"><strong>{item.title}</strong><span className="profile-tag">{visibilityLabels[item.visibility]}</span></div>
              {item.itemType === 'EXPERIENCE' && <p className="profile-muted">{[experienceLabels[item.subType ?? ''], item.role, item.startDate && `${item.startDate} ~ ${item.endDate ?? '진행 중'}`].filter(Boolean).join(' · ')}</p>}
              {(item.issuer || (item.startDate && item.itemType !== 'EXPERIENCE')) && <p className="profile-muted">{[item.issuer, item.startDate].filter(Boolean).join(' · ')}</p>}
              {item.description && <p>{item.description}</p>}
              {href && <a href={href} target="_blank" rel="noreferrer">{item.url}</a>}
            </li>
          })}</ul> : <p className="profile-muted">등록한 항목이 없어요.</p>}
        </section>
      })}
    </>
  )
}
