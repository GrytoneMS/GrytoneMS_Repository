import { categoryLabels, locationLabels, meetingLabels } from './model'
import type { Category, LocationCode, MeetingType, RoleCode, Sort } from './model'
import { roleLabels } from './create-model'

export const periodLabels = { WEEK: '1주 이내', MONTH: '1개월 이내', THREE_MONTHS: '3개월 이내', OVER_THREE_MONTHS: '3개월 이상' }
export type Period = keyof typeof periodLabels
export type PostFilters = {
  category: Category[]; roleCode: RoleCode[]; schoolId: string[];
  locationCode: LocationCode[]; meetingType: MeetingType[]; period?: Period; openOnly: boolean; keyword: string
}
export type PostListConditions = PostFilters & { sort: Sort }
export const multiFilterKeys = ['category', 'roleCode', 'schoolId', 'locationCode', 'meetingType'] as const
export type MultiFilterKey = typeof multiFilterKeys[number]
export const filterLabels = { category: '모집 유형', roleCode: '모집 역할', schoolId: '팀장 학교', locationCode: '지역·모임 장소', meetingType: '진행 방식' }
export const filterOptions = { category: categoryLabels, roleCode: roleLabels, locationCode: locationLabels, meetingType: meetingLabels }
export function defaultConditions(): PostListConditions {
  return { sort: 'LATEST', category: [], roleCode: [], schoolId: [], locationCode: [], meetingType: [], openOnly: true, keyword: '' }
}
// Long 학교 ID는 정밀도를 잃지 않도록 문자열로 유지합니다.
function schoolId(value: string) { return /^[1-9]\d*$/.test(value) && (value.length < 19 || value.length === 19 && value <= '9223372036854775807') }
export function readListConditions(params: URLSearchParams): PostListConditions {
  const result = defaultConditions()
  result.sort = params.get('sort') === 'DEADLINE' ? 'DEADLINE' : 'LATEST'
  for (const key of multiFilterKeys) {
    const values = params.getAll(key).flatMap(value => value.split(','))
    const valid = values.filter(value => key === 'schoolId' ? schoolId(value) : Object.hasOwn(filterOptions[key], value))
    // URL의 반복·순서 차이로 같은 조건을 다르게 취급하지 않습니다.
    Object.assign(result, { [key]: [...new Set(valid)].sort() })
  }
  const period = params.get('period')
  if (period && Object.hasOwn(periodLabels, period)) result.period = period as Period
  result.openOnly = params.get('openOnly') !== 'false'
  result.keyword = (params.get('keyword') ?? '').trim()
  return result
}
export function appendPostFilters(params: URLSearchParams, filters: Partial<PostFilters>) {
  for (const key of multiFilterKeys) for (const value of filters[key] ?? []) params.append(key, value)
  if (filters.period) params.set('period', filters.period)
  if (filters.openOnly !== undefined) params.set('openOnly', String(filters.openOnly))
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim())
  return params
}
export function listSearchParams(conditions: PostListConditions, previous = new URLSearchParams()) {
  const params = appendPostFilters(new URLSearchParams({ sort: conditions.sort }), conditions)
  // 개발용 시나리오는 보존하되 커서는 URL 상태로 복원하지 않습니다.
  for (const key of ['scenario', 'viewer']) if (previous.has(key)) params.set(key, previous.get(key)!)
  return params
}
export function keywordError(keyword: string) {
  return keyword.trim().length === 1 ? '검색어는 2자 이상 입력해 주세요.' : ''
}
export function resetFilters(conditions: PostListConditions): PostListConditions {
  return { ...defaultConditions(), sort: conditions.sort, keyword: conditions.keyword }
}
export function clearAllConditions(previous: PostListConditions) { return { ...defaultConditions(), sort: previous.sort } }

export const recentSearchKey = 'teamon.posts.recent-searches'
export function parseRecentSearches(value: string | null): string[] {
  try {
    const data: unknown = JSON.parse(value ?? '[]')
    return Array.isArray(data) ? [...new Set(data.filter((item): item is string => typeof item === 'string' && item.trim().length >= 2).map(item => item.trim()))].slice(0, 10) : []
  } catch { return [] }
}
export function rememberSearch(recent: string[], keyword: string) {
  const trimmed = keyword.trim()
  return trimmed.length >= 2 ? [trimmed, ...recent.filter(value => value !== trimmed)].slice(0, 10) : recent
}
