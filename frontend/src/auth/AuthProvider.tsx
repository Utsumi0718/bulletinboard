import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { getCurrentUser, type CurrentUser } from '../api/auth.ts'
import { ApiError, toUserFacingError } from '../api/client.ts'
import { AuthContext, type AuthState } from './AuthContext.ts'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null, error: null })
  const requestId = useRef(0)

  const load = useCallback(async (signal?: AbortSignal): Promise<CurrentUser | null> => {
    const id = ++requestId.current
    try {
      const user = await getCurrentUser(undefined, signal)
      if (id === requestId.current && !signal?.aborted) {
        setState(user
          ? { status: 'authenticated', user, error: null }
          : { status: 'anonymous', user: null, error: null })
      }
      return user
    } catch (error) {
      if (signal?.aborted || id !== requestId.current) throw error
      if (error instanceof ApiError && error.status === 401) {
        setState({ status: 'anonymous', user: null, error: null })
        return null
      }
      setState({ status: 'error', user: null, error: toUserFacingError(error) })
      throw error
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    const id = ++requestId.current
    void getCurrentUser(undefined, controller.signal).then((user) => {
      if (id === requestId.current && !controller.signal.aborted) {
        setState(user
          ? { status: 'authenticated', user, error: null }
          : { status: 'anonymous', user: null, error: null })
      }
    }).catch((error: unknown) => {
      if (id !== requestId.current || controller.signal.aborted) return
      if (error instanceof ApiError && error.status === 401) {
        setState({ status: 'anonymous', user: null, error: null })
      } else {
        setState({ status: 'error', user: null, error: toUserFacingError(error) })
      }
    })
    return () => controller.abort()
  }, [])

  const clear = useCallback(() => {
    requestId.current += 1
    setState({ status: 'anonymous', user: null, error: null })
  }, [])

  const refresh = useCallback(() => {
    setState({ status: 'loading', user: null, error: null })
    return load()
  }, [load])

  return <AuthContext value={{ state, refresh, clear }}>{children}</AuthContext>
}
