import { useEffect, useState } from 'react'
import { Button } from '../../components/Button'
import { Input } from '../../components/Input'
import { getSchools, type SchoolOption } from './api'
import { postErrorMessage } from './errors'
import { filterLabels, filterOptions, keywordError, multiFilterKeys, parseRecentSearches, periodLabels, recentSearchKey, rememberSearch, resetFilters } from './list-query'
import type { MultiFilterKey, Period, PostListConditions } from './list-query'
import './list-filters.css'

type Change = (update: (previous: PostListConditions) => PostListConditions) => void
function SearchForm({ keyword, onSearch }: { keyword: string; onSearch: (value: string) => void }) {
  const [draft, setDraft] = useState(keyword)
  const [error, setError] = useState(keywordError(keyword))
  return <form className="post-search" role="search" onSubmit={event => {
    event.preventDefault()
    const message = keywordError(draft)
    setError(message)
    if (!message) { setDraft(draft.trim()); onSearch(draft.trim()) }
  }}>
    <div className="post-search-input"><img src={`${import.meta.env.BASE_URL}posts/search.svg`} alt="" width="20" height="20" />
      <Input type="search" label="모집글 검색" placeholder="제목, 소개, 행사명으로 검색 (2자 이상)" value={draft} error={error}
        onChange={event => { setDraft(event.target.value); setError('') }} />
    </div>
    <div className="post-search-actions"><Button type="submit">검색</Button><Button disabled={!draft && !keyword} onClick={() => { setDraft(''); setError(''); onSearch('') }}>검색어 지우기</Button></div>
  </form>
}

export function ListFilters({ conditions, change }: { conditions: PostListConditions; change: Change }) {
  const [schools, setSchools] = useState<SchoolOption[]>([])
  const [schoolState, setSchoolState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [schoolError, setSchoolError] = useState('')
  const [retry, setRetry] = useState(0)
  const [searchRevision, setSearchRevision] = useState(0)
  const [recent, setRecent] = useState<string[]>(() => {
    try { return parseRecentSearches(localStorage.getItem(recentSearchKey)) } catch { return [] }
  })
  useEffect(() => {
    const controller = new AbortController()
    getSchools({ signal: controller.signal }).then(result => {
      if (!controller.signal.aborted) { setSchools(result); setSchoolState('ready') }
    }).catch(error => {
      if (!controller.signal.aborted) { setSchoolError(postErrorMessage(error)); setSchoolState('error') }
    })
    return () => controller.abort()
  }, [retry])
  function saveRecent(values: string[]) {
    setRecent(values)
    try { localStorage.setItem(recentSearchKey, JSON.stringify(values)) } catch { /* 저장 차단 시 현재 탭에서만 유지합니다. */ }
  }
  function search(keyword: string) {
    if (keyword) saveRecent(rememberSearch(recent, keyword))
    change(previous => ({ ...previous, keyword }))
  }
  function toggle(key: MultiFilterKey, value: string) {
    change(previous => {
      const selected: readonly string[] = previous[key]
      return { ...previous, [key]: selected.includes(value) ? selected.filter(item => item !== value) : [...selected, value] }
    })
  }
  const choices = (key: MultiFilterKey): [string, string][] => key === 'schoolId'
    ? schools.map(school => [String(school.id), [school.name, school.campus].filter(Boolean).join(' · ')])
    : Object.entries(filterOptions[key])
  const label = (key: MultiFilterKey, value: string) => choices(key).find(([id]) => id === value)?.[1] ?? `학교 ID ${value}`
  const hasFilters = multiFilterKeys.some(key => conditions[key].length) || !!conditions.period || !conditions.openOnly
  return <>
    <SearchForm key={`${conditions.keyword}:${searchRevision}`} keyword={conditions.keyword} onSearch={search} />
    {recent.length > 0 && <div className="post-recent" aria-label="최근 검색어"><span>최근 검색어</span>{recent.map(value => <span className="post-recent-item" key={value}>
      <button type="button" onClick={() => { setSearchRevision(current => current + 1); search(value) }}>{value}</button><button type="button" aria-label={`최근 검색어 ${value} 삭제`} onClick={() => saveRecent(recent.filter(item => item !== value))}>×</button>
    </span>)}<Button onClick={() => saveRecent([])}>기록 지우기</Button></div>}
    <div className="post-filters" aria-label="모집글 필터">
      {multiFilterKeys.map(key => <details className="post-filter" name="post-filter" key={key} onKeyDown={event => {
        if (event.key === 'Escape') { event.currentTarget.open = false; event.currentTarget.querySelector('summary')?.focus() }
      }}>
        <summary>{filterLabels[key]}{conditions[key].length > 0 && ` · ${conditions[key].length}`} <span aria-hidden="true">▾</span></summary>
        <div className="post-filter-menu"><fieldset><legend>{filterLabels[key]} · 여러 개 선택</legend>
          {key === 'schoolId' && (schoolState === 'loading' ? <p role="status">학교 목록을 불러오는 중…</p> : schoolState === 'error' ? <div role="alert"><p>{schoolError}</p><Button onClick={() => { setSchoolState('loading'); setRetry(value => value + 1) }}>학교 목록 다시 시도</Button></div> : schools.length === 0 && <p>등록된 학교가 없습니다.</p>)}
          {choices(key).map(([value, text]) => <label key={value}><input type="checkbox" checked={(conditions[key] as readonly string[]).includes(value)} onChange={() => toggle(key, value)} />{text}</label>)}
        </fieldset></div>
      </details>)}
      <details className="post-filter" name="post-filter" onKeyDown={event => { if (event.key === 'Escape') { event.currentTarget.open = false; event.currentTarget.querySelector('summary')?.focus() } }}>
        <summary>활동 기간{conditions.period && ` · ${periodLabels[conditions.period]}`} <span aria-hidden="true">▾</span></summary>
        <div className="post-filter-menu"><fieldset><legend>활동 기간 · 하나 선택</legend>
          {([['', '전체 기간'], ...Object.entries(periodLabels)] as [string, string][]).map(([value, text]) => <label key={value}><input type="radio" name="post-period" checked={(conditions.period ?? '') === value} onChange={() => change(previous => ({ ...previous, period: value ? value as Period : undefined }))} />{text}</label>)}
        </fieldset></div>
      </details>
      <Button className="post-open-only" aria-pressed={conditions.openOnly} onClick={() => change(previous => ({ ...previous, openOnly: !previous.openOnly }))}>모집 중만 보기 · {conditions.openOnly ? 'ON' : 'OFF'}</Button>
      <Button disabled={!hasFilters} onClick={() => change(resetFilters)}>필터 초기화</Button>
    </div>
    <div className="post-selected" aria-label="선택한 검색·필터 조건">
      {conditions.keyword && <Button aria-label={`검색어 ${conditions.keyword} 해제`} onClick={() => search('')}>검색: {conditions.keyword} ×</Button>}
      {multiFilterKeys.flatMap(key => conditions[key].map(value => <Button key={`${key}:${value}`} aria-label={`${filterLabels[key]} ${label(key, value)} 해제`} onClick={() => toggle(key, value)}>{filterLabels[key]}: {label(key, value)} ×</Button>))}
      {conditions.period && <Button onClick={() => change(previous => ({ ...previous, period: undefined }))}>활동 기간: {periodLabels[conditions.period]} ×</Button>}
      {!conditions.openOnly && <Button onClick={() => change(previous => ({ ...previous, openOnly: true }))}>마감된 글 포함 ×</Button>}
    </div>
    {hasFilters && <p className="post-note">필터 간에는 모두 일치하는 글, 같은 필터 안에서는 하나라도 일치하는 글을 찾아요.</p>}
  </>
}
