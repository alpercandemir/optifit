import type { Job, Preferences, Session } from './types'
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message)
  }
}
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      credentials: 'same-origin',
      signal: AbortSignal.timeout(15000),
      ...options,
    })
  } catch {
    throw new ApiError(
      0,
      'NETWORK',
      'Unable to connect. Check your internet connection and try again.',
    )
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      error.code ?? 'UNKNOWN',
      error.message ?? 'The request could not be completed. Try again.',
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
export const getSession = () => request<Session>('/session')
export const getJob = (id: string) => request<Job>(`/recommendations/${encodeURIComponent(id)}`)
export const getExamples = () => request<Job>('/examples')
export function createJob(file: File, prefs: Preferences, key: string, session: Session) {
  const form = new FormData()
  form.set('photo', file)
  form.set('category', prefs.category)
  form.set('style', prefs.style)
  form.set('color', prefs.color)
  form.set('consent', String(prefs.consent))
  if (prefs.budget) form.set('budget', prefs.budget)
  return request<Job>('/recommendations', {
    method: 'POST',
    headers: { [session.csrfHeader]: session.csrfToken, 'Idempotency-Key': key },
    body: form,
  })
}
export function deleteJob(id: string, session: Session) {
  return request<void>(`/recommendations/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: { [session.csrfHeader]: session.csrfToken },
  })
}
export function safeLink(url: string): string | undefined {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' && !parsed.username && !parsed.password
      ? parsed.href
      : undefined
  } catch {
    return undefined
  }
}
export async function shareLink(
  title: string,
  url: string,
): Promise<'shared' | 'copied' | 'cancelled'> {
  const safe = safeLink(url)
  if (!safe) throw new Error('This link is unavailable.')
  if (navigator.share) {
    try {
      await navigator.share({ title, url: safe })
      return 'shared'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled'
    }
  }
  try {
    await navigator.clipboard.writeText(safe)
    return 'copied'
  } catch {
    throw new Error(
      'The link could not be copied. Open the product page and copy the address from your browser.',
    )
  }
}
