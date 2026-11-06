import { isAxiosError } from 'axios'

export function sourceSaveConflict(error: unknown, sourceId: string | null): 'stale-edit' | 'duplicate-key' | null {
  if (!isAxiosError<{ code?: string }>(error) || error.response?.status !== 409) return null
  if (error.response.data?.code === 'SOURCE_TIME_CONFLICT' && sourceId !== null) return 'stale-edit'
  if (error.response.data?.code === 'SOURCE_BUSINESS_KEY_CONFLICT') return 'duplicate-key'
  return null
}
