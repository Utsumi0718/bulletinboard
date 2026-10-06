import { createContext, useContext } from 'react'
import type { CurrentUser } from '../api/auth.ts'
import type { UserFacingError } from '../api/client.ts'

export type AuthState =
  | { status: 'loading' | 'anonymous'; user: null; error: null }
  | { status: 'authenticated'; user: CurrentUser; error: null }
  | { status: 'error'; user: null; error: UserFacingError }

export type AuthContextValue = {
  state: AuthState
  refresh: () => Promise<CurrentUser | null>
  clear: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth requires AuthProvider')
  return context
}
