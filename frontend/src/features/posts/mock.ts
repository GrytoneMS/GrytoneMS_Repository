import type { ApplicationStatus, Category, PostDetail, PostPage, PostSummary, Sort, Viewer } from './model'

const titles = ['○○ 영상 공모전 팀원 구해요', '토익 800+ 같이 달려요', '해커톤 같이 나갈 개발자', '앱 UI 스프린트 2주', '광고 마케팅 공모전 기획팀', '브랜딩 포트폴리오 작업', '교내외 PT 대회 발표자', '영상 편집 스터디', '캠페인 카피 공모전', '데이터 분석 경진대회', '숏폼 콘텐츠 제작팀', 'UX 리서치 입문']
const categories: Category[] = ['CONTEST', 'STUDY', 'COMPETITION', 'PROJECT']
const dayOffsets = [3, 9, 6, 14, 11, 20, 8, 15, 2, 12, 7, -1]
// 예시 날짜도 KST를 기준으로 생성합니다. 실서버 dDay는 재계산하지 않습니다.
const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' })
function dateAfter(days: number) {
  const date = new Date(`${today}T00:00:00+09:00`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' })
}
export const mockPosts: PostDetail[] = titles.map((title, index) => ({
  id: index + 1, title, category: categories[index % 4], status: index === 11 ? 'CLOSED' : 'OPEN',
  deadline: dateAfter(dayOffsets[index]), dDay: dayOffsets[index],
  createdAt: `${dateAfter(-index)}T12:00:00`, meetingType: index === 1 ? 'ONLINE' : 'OFFLINE',
  roles: index === 0 ? [
    { id: 31, roleCode: 'VIDEO', roleName: '영상 편집', headcount: 1, filledCount: 0, requirements: 'Premiere 가능자 우대', status: 'OPEN' },
    { id: 32, roleCode: 'DESIGN', roleName: '디자인', headcount: 2, filledCount: 1, requirements: 'Figma 가능자', status: 'OPEN' },
  ] : [{ id: 100 + index, roleCode: 'ETC', roleName: index === 1 ? '스터디원' : '팀원', headcount: 3, filledCount: index === 11 ? 3 : 1, requirements: index === 1 ? null : index === 2 ? '' : index === 3 ? '   ' : '함께 꾸준히 활동할 분', status: index === 11 ? 'CLOSED' : 'OPEN' }],
  leader: { id: 1, nickname: '민지', schoolName: '단국대학교', major: '컴퓨터공학', profileImageUrl: null },
  description: '천안·아산 대학생들과 함께 결과물을 만들 팀원을 모집해요.\n매주 진행 상황을 공유하고, 서로의 아이디어를 발전시키며 프로젝트를 완성하려고 합니다.',
  periodStart: dateAfter(21), periodEnd: dateAfter(50),
  locationCode: index === 1 ? null : 'CHEONAN_TERMINAL', locationText: null, meetingFreq: index === 1 ? null : '주 1회',
  event: ['CONTEST', 'COMPETITION'].includes(categories[index % 4]) ? { name: index === 0 ? '○○ 대학생 영상 공모전' : `개발용 예시 행사 ${index + 1}`, host: '○○ 재단', applyDeadline: dateAfter(25), officialUrl: 'https://example.com/contest' } : null,
  isMine: false, myApplication: null,
}))

function summary(post: PostDetail): PostSummary {
  // 상세 전용 정보(위치·총 정원·권한 등)가 목록에 섞이지 않도록 명시적으로 선택합니다.
  return {
    id: post.id, title: post.title, category: post.category, status: post.status,
    deadline: post.deadline, dDay: post.dDay, meetingType: post.meetingType, createdAt: post.createdAt,
    leader: { id: post.leader.id, nickname: post.leader.nickname, schoolName: post.leader.schoolName },
    roles: post.roles.map(role => ({ roleCode: role.roleCode, roleName: role.roleName, remaining: role.headcount - role.filledCount })),
  }
}
const applicationStates: Partial<Record<Viewer, ApplicationStatus>> = {
  applied: 'PENDING', accepted: 'ACCEPTED', rejected: 'REJECTED', canceled: 'CANCELED', applicationClosed: 'CLOSED', left: 'LEFT',
}
export function mockDetail(id: string, viewer: Viewer): PostDetail {
  const post = mockPosts.find(item => String(item.id) === id)
  if (!post) throw new Error('모집글을 찾을 수 없습니다.')
  const status = applicationStates[viewer]
  return { ...post, isMine: viewer === 'owner', myApplication: status ? { id: 55, postRoleId: post.roles[0].id, status } : null }
}

// mock 저장소만 커서를 해석합니다. UI와 실제 API 계층에서는 opaque 문자열로 취급합니다.
export function mockList(sort: Sort, cursor?: string, size = 20, filters: Partial<import('./list-query').PostFilters> = {}): PostPage {
  if (filters.schoolId?.length) throw new Error('예시 데이터에는 학교 ID가 없습니다. 학교 필터는 API 모드에서 확인해 주세요.')
  const periods = { WEEK: [0, 7], MONTH: [0, 31], THREE_MONTHS: [0, 92], OVER_THREE_MONTHS: [93, 100000] }
  const keyword = filters.keyword?.trim() ?? ''
  const sorted = mockPosts.filter(post => {
    const days = (Date.parse(post.periodEnd) - Date.parse(post.periodStart)) / 86400000
    return (!filters.openOnly || post.status === 'OPEN')
      && (!filters.category?.length || filters.category.includes(post.category))
      && (!filters.roleCode?.length || post.roles.some(role => filters.roleCode!.includes(role.roleCode)))
      && (!filters.locationCode?.length || post.locationCode !== null && filters.locationCode.includes(post.locationCode))
      && (!filters.meetingType?.length || filters.meetingType.includes(post.meetingType))
      && (!filters.period || days >= periods[filters.period][0] && days <= periods[filters.period][1])
      && (!keyword || [post.title, post.description, post.event?.name ?? ''].some(text => text.includes(keyword)))
  }).sort((a, b) => sort === 'DEADLINE'
    ? a.deadline.localeCompare(b.deadline) || b.createdAt.localeCompare(a.createdAt)
    : b.createdAt.localeCompare(a.createdAt))
  const offsets = new Map(sorted.map((post, index) => [`sample-after-${post.id}`, index + 1]))
  const offset = cursor === undefined ? 0 : offsets.get(cursor)
  if (offset === undefined) throw new Error('개발용 커서가 올바르지 않습니다.')
  const items = sorted.slice(offset, offset + size)
  const hasNext = offset + size < sorted.length
  return { items: items.map(summary), nextCursor: hasNext ? `sample-after-${items.at(-1)!.id}` : null, hasNext }
}
