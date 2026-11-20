import { ApiError } from '../../api/client'
import type { EditSnapshot } from './edit-model'
import { checkSnapshot, toEditRequest } from './edit-model'
import type { PostDraft } from './create-model'
import { mockDetail, mockPosts } from './mock'

export const editExamples = { success: '정상 수정', invalid: '서버 필드 오류', forbidden: '권한 없음', restricted: '수정 중 지원 현황 변경', failure: '서버 실패', loadFailure: '초기 조회 실패', contactFailure: '연락처 조회 실패', missing: '없는 글', deleted: '삭제된 글', closed: '마감된 글', refreshFailure: '저장 성공 후 상세 조회 실패', noAuth: '토큰 없음' }
const exampleContacts = new Map<number, string>()
export async function loadEditExample(id: string, example: string): Promise<EditSnapshot> {
  await new Promise(resolve => setTimeout(resolve, 350))
  if (example === 'loadFailure' || example === 'contactFailure') throw new Error('개발 예시: 초기값 조회 실패')
  if (['forbidden', 'missing', 'deleted'].includes(example)) throw new ApiError(example === 'forbidden' ? 403 : 404, { success: false, code: example === 'forbidden' ? 'FORBIDDEN' : example === 'deleted' ? 'POST_DELETED' : 'NOT_FOUND', message: '개발 예시 오류' })
  const original = mockDetail(id, 'owner')
  const post = { ...original, roles: original.roles.map(role => ({ ...role })), event: original.event ? { ...original.event } : null }
  if (example === 'closed') { post.status = 'CLOSED'; post.deadline = '2020-01-01' }
  return checkSnapshot({ post, contactLink: exampleContacts.get(post.id) ?? 'https://open.kakao.com/o/existing?keep=1', applicants: post.roles.map((role, index) => ({ postRoleId: role.id, roleName: role.roleName, headcount: role.headcount, filledCount: role.filledCount,
    applications: index === 0 ? [] : [{ id: 50 + index, status: 'ACCEPTED', message: '', availableTime: '', createdAt: '', applicant: { id: 2, nickname: '예시 지원자', schoolName: null, major: null, grade: null, profileImageUrl: null } }] })) }, id)
}
export async function saveEditExample(draft: PostDraft, snapshot: EditSnapshot, example: string, signal?: AbortSignal) {
  await new Promise(resolve => setTimeout(resolve, 700))
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
  if (example === 'invalid') throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '입력 내용을 확인해 주세요.', fieldErrors: { title: '개발 예시: 제목을 다시 확인해 주세요.', 'roles[0].roleName': '개발 예시: 역할 이름을 확인해 주세요.' } })
  if (example === 'restricted') throw new ApiError(409, { success: false, code: 'ROLE_HAS_APPLICANTS', message: '지원 현황 변경' })
  if (example === 'failure') throw new ApiError(500, { success: false, code: 'INTERNAL_ERROR', message: '개발 예시: 서버 오류입니다. 입력은 유지됩니다.' })
  if (example === 'forbidden') throw new ApiError(403, { success: false, code: 'FORBIDDEN', message: '권한 변경' })
  const request = toEditRequest(draft, snapshot)
  const { contactLink, ...publicFields } = request
  const updated = { ...snapshot.post, ...publicFields, roles: request.roles.map((r, index) => ({ ...r, id: r.id ?? 10000 + index, filledCount: snapshot.post.roles.find(old => old.id === r.id)?.filledCount ?? 0, status: snapshot.post.status })) }
  const index = mockPosts.findIndex(p => p.id === updated.id)
  if (index >= 0) mockPosts[index] = updated
  exampleContacts.set(updated.id, contactLink)
  return updated
}
