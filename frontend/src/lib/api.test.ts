import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createJob, request, safeLink, shareLink } from './api'

afterEach(() => vi.unstubAllGlobals())
describe('safe external links', () => {
  it('rejects scripts, relative URLs, credentials and insecure links', () => {
    for (const url of [
      'javascript:alert(1)',
      '/api/session',
      'http://example.com',
      'https://user:secret@example.com',
      'not-a-url',
    ])
      expect(safeLink(url)).toBeUndefined()
    expect(safeLink('https://www.atasunoptik.com.tr/product')).toBe(
      'https://www.atasunoptik.com.tr/product',
    )
  })
  it('does not copy a link when the user cancels native sharing', async () => {
    const writeText = vi.fn()
    vi.stubGlobal('navigator', {
      share: vi.fn().mockRejectedValue(new DOMException('cancelled', 'AbortError')),
      clipboard: { writeText },
    })
    expect(await shareLink('Product', 'https://example.com/p')).toBe('cancelled')
    expect(writeText).not.toHaveBeenCalled()
  })
  it('uses clipboard fallback when native sharing is unavailable', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    expect(await shareLink('Product', 'https://example.com/p')).toBe('copied')
    expect(writeText).toHaveBeenCalledWith('https://example.com/p')
  })
})
describe('API requests', () => {
  it('sends CSRF, idempotency, consent and filters without exposing session tokens in URLs', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{"jobId":"one"}', { status: 202 }))
    vi.stubGlobal('fetch', fetch)
    await createJob(
      new File(['photo'], 'face.png', { type: 'image/png' }),
      { category: 'SUNGLASSES', budget: '3000', style: 'CLASSIC', color: 'ANY', consent: true },
      'unique-key',
      { csrfToken: 'csrf', csrfHeader: 'X-CSRF-TOKEN', mode: 'demo', resultTtlSeconds: 3600 },
    )
    const [url, options] = fetch.mock.calls[0]!
    expect(url).toBe('/api/v1/recommendations')
    expect(options.credentials).toBe('same-origin')
    expect(options.headers['X-CSRF-TOKEN']).toBe('csrf')
    expect(options.body.get('budget')).toBe('3000')
    expect(options.body.get('consent')).toBe('true')
    expect(options.headers['Idempotency-Key']).toBe('unique-key')
  })
  it('preserves server errors and normalizes network errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('{"code":"NOT_FOUND","message":"Expired"}', { status: 404 }),
        ),
    )
    await expect(request('/recommendations/deleted')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
      message: 'Expired',
    })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(request('/session')).rejects.toBeInstanceOf(ApiError)
  })
})
