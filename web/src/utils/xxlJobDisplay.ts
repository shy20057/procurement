export function safeXxlAdminUrl(value: string | null | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString().replace(/\/$/, '') : null
  } catch {
    return null
  }
}

export function shortXxlLogKey(value: string | null | undefined): string {
  if (!value) return '—'
  return value.length <= 18 ? value : `${value.slice(0, 12)}…${value.slice(-4)}`
}
