import { describe, expect, it } from 'vitest'

import request from './request'

describe('request URL composition', () => {
  it('keeps an SRM API path under a single /api prefix', () => {
    expect(request.getUri({ url: '/api/srm/invitations' })).toBe('/api/srm/invitations')
  })
})
