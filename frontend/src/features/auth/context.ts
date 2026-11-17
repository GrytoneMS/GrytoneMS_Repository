import { createContext } from 'react'
import { createAuthStore, type AuthStore } from './session'
export const AuthContext = createContext<AuthStore>(createAuthStore(() => null))
