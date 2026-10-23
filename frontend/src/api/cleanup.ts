import { api, type ApiClient } from './client.ts'

export type CleanupRow = Record<string, string | number | null>
export type CleanupPreview = {
  topicId: number; reason: string; deletedAt: string | null; eligibleAtUtc: string | null
  elapsedHours: number | null; imageOnly: boolean
}

export function createCleanupApi(client: ApiClient = api) {
  return {
    runs: (page = 0, signal?: AbortSignal) => client.request<CleanupRow[]>(`/api/admin/topic-cleanup/runs?page=${page}&size=20`, { signal }),
    targets: (page = 0, signal?: AbortSignal) => client.request<CleanupRow[]>(`/api/admin/topic-cleanup/targets?page=${page}&size=20`, { signal }),
    attempts: (id: number, page = 0, signal?: AbortSignal) => client.request<CleanupRow[]>(`/api/admin/topic-cleanup/targets/${id}/attempts?page=${page}&size=20`, { signal }),
    preview: (signal?: AbortSignal) => client.request<CleanupPreview[]>('/api/admin/topic-cleanup/dry-run', { signal }),
    resume: (id: number, reason: string) => client.request<void>(`/api/admin/topic-cleanup/targets/${id}/resume`, { method: 'POST', json: { reason } }),
  }
}
export const cleanupApi = createCleanupApi()
