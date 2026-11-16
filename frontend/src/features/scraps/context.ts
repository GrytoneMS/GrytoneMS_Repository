import { createContext, useContext } from 'react'
import type { createScrapController } from './controller'

export type ScrapContextValue = { controller: ReturnType<typeof createScrapController>; mock: boolean; userId?: number | string; token?: string; search: string }
export const ScrapContext = createContext<ScrapContextValue | null>(null)
export const useScraps = () => useContext(ScrapContext)
