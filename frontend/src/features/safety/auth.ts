import { useEffect, useState } from 'react'
import { readAccessToken } from '../auth/api'

const readSafetyToken = () => typeof sessionStorage === 'undefined' ? null : readAccessToken()?.trim() || null

// #47 병합 후 useAuth().scope/session 및 store.execute로 교체할 경계.
// 기존 토큰 저장 키/우선순위를 그대로 사용하며 새 저장소는 만들지 않습니다.
export function useSafetyToken() {
  const [token, setToken] = useState(readSafetyToken)
  useEffect(() => {
    const sync = () => setToken(readSafetyToken())
    window.addEventListener('storage', sync)
    window.addEventListener('focus', sync)
    const timer = window.setInterval(sync, 400)
    return () => { window.removeEventListener('storage', sync); window.removeEventListener('focus', sync); clearInterval(timer) }
  }, [])
  return token
}
export const safetyTokenCurrent = (token: string) => readSafetyToken() === token
