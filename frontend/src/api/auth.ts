import { ApiError, api, type ApiClient } from './client.ts'

export type CurrentUser = {
  userId: number
  profileId: number | null
  username: string
  role: string
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export async function getCurrentUser(client: ApiClient = api, signal?: AbortSignal): Promise<CurrentUser | null> {
  const result = await client.request<unknown>('/api/auth/me', { signal })
  if (!isObject(result) || typeof result.authenticated !== 'boolean') {
    throw new ApiError('invalid-response', 200, '認証状態を確認できませんでした。')
  }
  if (!result.authenticated) return null
  const profileId = result.profileId === undefined ? null : result.profileId
  if (typeof result.userId !== 'number' || !Number.isInteger(result.userId) || result.userId < 1
      || (profileId !== null && (typeof profileId !== 'number' || !Number.isInteger(profileId) || profileId < 1))
      || typeof result.username !== 'string' || typeof result.role !== 'string') {
    throw new ApiError('invalid-response', 200, '認証状態を確認できませんでした。')
  }
  return {
    userId: result.userId,
    profileId,
    username: result.username,
    role: result.role,
  }
}
