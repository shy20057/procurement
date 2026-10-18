import { describe, expect, it } from 'vitest'

import { safeXxlAdminUrl, shortXxlLogKey } from './xxlJobDisplay'

describe('XXL-JOB display helpers', () => {
  it('accepts only HTTP admin links', () => {
    expect(safeXxlAdminUrl('http://localhost:9090/xxl-job-admin')).toBe('http://localhost:9090/xxl-job-admin')
    expect(safeXxlAdminUrl('https://jobs.example.test/xxl-job-admin')).toBe('https://jobs.example.test/xxl-job-admin')
    expect(safeXxlAdminUrl('javascript:alert(1)')).toBeNull()
    expect(safeXxlAdminUrl(null)).toBeNull()
  })

  it('shortens a log digest without exposing a filesystem path', () => {
    expect(shortXxlLogKey('1234567890abcdef'.repeat(4))).toBe('1234567890ab…cdef')
    expect(shortXxlLogKey(null)).toBe('—')
  })
})
