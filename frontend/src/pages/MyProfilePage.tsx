import { Link } from 'react-router'
import { MyProfileData, ProfileAvatar, ProfileProgress } from '../features/profile/ProfileLayout'
import { schoolName } from '../features/profile/presentation'
import { ProfileSections } from '../features/profile/ProfileSections'
import type { Profile } from '../features/profile/api'
import { roleLabels } from '../features/posts/create-model'

export default function MyProfilePage() {
  return <MyProfileData>{profile => <MyProfile profile={profile} />}</MyProfileData>
}

function MyProfile({ profile }: { profile: Profile }) {
  return <div className="profile-view-grid">
    <aside className="profile-stack">
      <section className="profile-card profile-overview">
        <ProfileAvatar url={profile.profileImageUrl} />
        <h1>{profile.nickname}</h1>
        <p className="profile-muted">{[schoolName(profile) + (profile.verified ? ' (인증)' : ''), profile.major, profile.grade && `${profile.grade}학년`].filter(Boolean).join(' · ')}</p>
        {profile.bio && <p>{profile.bio}</p>}
        <div className="profile-pills">{profile.roleTags.map(role => <span key={role}>{roleLabels[role]}</span>)}</div>
        <ProfileProgress value={profile.completeness} />
        <Link className="button profile-primary" to="/me/profile/edit">프로필 편집</Link>
      </section>
      <section className="profile-card profile-shortcuts" aria-label="내 활동 바로가기">
        <Link to="/me/posts">내 모집글</Link>
        <Link to="/me/applications">내 지원 내역</Link>
        <Link to="/me/blocks">내 차단 목록</Link>
        <Link to="/me/scraps">내 스크랩</Link>
      </section>
    </aside>
    <div className="profile-stack">
      <ProfileSections profile={profile} owner />
    </div>
  </div>
}
