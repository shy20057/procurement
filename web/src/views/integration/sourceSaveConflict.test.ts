import { describe, expect, it } from 'vitest'
import { sourceSaveConflict } from './sourceSaveConflict'

const conflict = (code: string, status = 409) => ({ isAxiosError: true, response: { status, data: { code } } })

describe('source maintenance conflict classification', () => {
  it('requires refresh only for an existing record with a stale source timestamp', () => {
    expect(sourceSaveConflict(conflict('SOURCE_TIME_CONFLICT'), '9007199254740993')).toBe('stale-edit')
    expect(sourceSaveConflict(conflict('SOURCE_TIME_CONFLICT'), null)).toBeNull()
  })

  it('keeps duplicate business key creation editable rather than requiring a missing source ID', () => {
    expect(sourceSaveConflict(conflict('SOURCE_BUSINESS_KEY_CONFLICT'), null)).toBe('duplicate-key')
  })

  it('does not treat other HTTP conflicts or failures as stale source edits', () => {
    expect(sourceSaveConflict(conflict('RUN_ALREADY_ACTIVE'), '12')).toBeNull()
    expect(sourceSaveConflict(conflict('SOURCE_TIME_CONFLICT', 503), '12')).toBeNull()
    expect(sourceSaveConflict(new Error('network error'), '12')).toBeNull()
  })
})
