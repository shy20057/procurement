import { describe, expect, it } from 'vitest'
import { checkpointCursor, checkpointLag, parseCheckpoint, prettyJson } from './checkpointDisplay'
import type { SyncCheckpoint } from '@/types/integration'

const checkpoint: SyncCheckpoint = {
  mode: 'TIME_WINDOW_V2', generation: 2, baselineComplete: true,
  baselineAnchorAt: '2026-11-05T00:00:00.123456Z', baselineMaxSourceId: '9007199254740993',
  baselineLastSourceId: '9007199254740993', completedWatermark: '2026-11-05T00:01:00.000000Z',
  activeWindowId: 'window-1', windowStartAt: '2026-11-04T23:56:00.000000Z',
  windowEndAt: '2026-11-05T00:10:00.000000Z',
  pageCursor: { updatedAt: '2026-11-05T00:02:00.123456Z', sourceId: '9007199254740995' },
  sourceTimeHighwater: '2026-11-05T00:12:00.000000Z', lastPageAt: null, pageSequence: 2,
}

describe('time window checkpoint display', () => {
  it('preserves source IDs above Number precision and all timestamp microseconds', () => {
    const raw = JSON.stringify(checkpoint)
    const decoded = parseCheckpoint(raw)
    expect(decoded?.pageCursor?.sourceId).toBe('9007199254740995')
    expect(checkpointCursor(decoded)).toBe('(2026-11-05T00:02:00.123456Z, 9007199254740995)')
    expect(JSON.parse(prettyJson(raw))).toEqual(checkpoint)
    expect(decoded?.completedWatermark).toBe('2026-11-05T00:01:00.000000Z')
  })

  it('shows baseline ID position without inventing a row percentage', () => {
    const baseline = { ...checkpoint, baselineComplete: false, baselineLastSourceId: '3' }
    expect(checkpointCursor(baseline)).toBe('ID 3 / 固定上界 9007199254740993')
    expect(checkpointLag(baseline, 120)).toBe('基线扫描中，尚无完整水位')
  })

  it('measures lag from the completed watermark, not the current page', () => {
    expect(checkpointLag(checkpoint, 120)).toBe('540 秒（截至最近源时间观测）')
    expect(checkpointLag({ ...checkpoint, completedWatermark: checkpoint.sourceTimeHighwater }, 120)).toBe('0 秒（截至最近源时间观测）')
  })

  it('does not interpret legacy or corrupt checkpoints as time windows', () => {
    expect(parseCheckpoint('{bad')).toBeNull()
    expect(parseCheckpoint('{"mode":"SOURCE_CURSOR","snapshotPosition":123}')).toBeNull()
    expect(checkpointCursor(null)).toBe('尚未建立检查点')
  })
})
