import { describe, expect, it } from 'vitest'
import { parsePurchaseOrderCheckpoint, purchaseOrderCursor } from './checkpointDisplay'

describe('purchase order checkpoint display', () => {
  it('preserves the full source ID and timestamp in the page cursor', () => {
    const checkpoint = parsePurchaseOrderCheckpoint(JSON.stringify({
      completedWatermark: '2026-11-05T00:01:00.000000Z',
      windowEndAt: '2026-11-05T00:10:00.000000Z',
      pageCursor: { updatedAt: '2026-11-05T00:02:00.123456Z', sourceId: '9007199254740995' },
    }))
    expect(purchaseOrderCursor(checkpoint)).toBe('(2026-11-05T00:02:00.123456Z, 9007199254740995)')
    expect(checkpoint?.completedWatermark).toBe('2026-11-05T00:01:00.000000Z')
  })

  it('shows no active cursor for completed windows and malformed data', () => {
    const checkpoint = parsePurchaseOrderCheckpoint('{"completedWatermark":"2026-11-05T00:01:00Z"}')
    expect(purchaseOrderCursor(checkpoint)).toBe('暂无活动页游标')
    expect(parsePurchaseOrderCheckpoint('{bad')).toBeNull()
  })
})
