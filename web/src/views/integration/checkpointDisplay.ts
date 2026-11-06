import type { SyncCheckpoint } from '@/types/integration'

interface PurchaseOrderCheckpoint {
  completedWatermark: string | null
  windowEndAt: string | null
  pageCursor: { updatedAt: string; sourceId: string } | null
}

export function parsePurchaseOrderCheckpoint(value: string | null | undefined): PurchaseOrderCheckpoint | null {
  if (!value) return null
  try {
    const state = JSON.parse(value) as Record<string, unknown> | null
    if (!state || !('completedWatermark' in state)) return null
    const cursor = state.pageCursor as Record<string, unknown> | null
    const sourceId = typeof cursor?.sourceId === 'string' ? cursor.sourceId
      : typeof cursor?.sourceId === 'number' && Number.isSafeInteger(cursor.sourceId) ? String(cursor.sourceId) : null
    return {
      completedWatermark: typeof state.completedWatermark === 'string' ? state.completedWatermark : null,
      windowEndAt: typeof state.windowEndAt === 'string' ? state.windowEndAt : null,
      pageCursor: typeof cursor?.updatedAt === 'string' && sourceId !== null ? { updatedAt: cursor.updatedAt, sourceId } : null,
    }
  } catch { return null }
}

export function purchaseOrderCursor(state: PurchaseOrderCheckpoint | null | undefined): string {
  const cursor = state?.pageCursor
  return cursor ? `(${cursor.updatedAt}, ${cursor.sourceId})` : '暂无活动页游标'
}

// Cursor times and IDs are opaque server values: never round or convert them to Number/Date.
export function parseCheckpoint(value: string | null | undefined): SyncCheckpoint | null {
  if (!value) return null
  try {
    const state = JSON.parse(value)
    return state && state.mode === 'TIME_WINDOW_V2' ? state as SyncCheckpoint : null
  } catch { return null }
}

export function checkpointCursor(state: SyncCheckpoint | null | undefined): string {
  if (!state) return '尚未建立检查点'
  if (!state.baselineComplete) return `ID ${state.baselineLastSourceId} / 固定上界 ${state.baselineMaxSourceId}`
  return state.pageCursor ? `(${state.pageCursor.updatedAt}, ${state.pageCursor.sourceId})` : '暂无活动页游标'
}

export function prettyJson(value: string | null | undefined): string {
  if (!value) return '—'
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
}

export function checkpointLag(state: SyncCheckpoint | null | undefined, settleDelaySeconds: number): string {
  if (!state) return '尚无源时间观测'
  if (!state.baselineComplete) return '基线扫描中，尚无完整水位'
  if (!state.sourceTimeHighwater || !state.completedWatermark) return '尚无源时间观测'
  const elapsed = Date.parse(state.sourceTimeHighwater) - settleDelaySeconds * 1000 - Date.parse(state.completedWatermark)
  return Number.isFinite(elapsed) ? `${Math.max(0, Math.ceil(elapsed / 1000))} 秒（截至最近源时间观测）` : '时间格式异常'
}
