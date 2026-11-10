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
