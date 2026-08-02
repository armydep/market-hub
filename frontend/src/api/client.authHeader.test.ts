import { afterEach, describe, expect, it, vi } from 'vitest'
import { getJson, postJson } from './client'

/**
 * No page calls an authenticated request yet in this slice — RequireAuth has
 * no caller until S6+ wraps a real protected route. This is the honest way
 * to verify the header-attachment seam anyway: mock fetch directly and
 * assert the header shape, rather than skip it or fake a caller that
 * doesn't belong here yet.
 */
describe('bearer token attachment', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('attaches an Authorization header to a GET when a token is given', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }))

    await getJson('/some/path', undefined, 'a.jwt.token')

    const [, init] = fetchSpy.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBe('Bearer a.jwt.token')
  })

  it('sends no Authorization header when no token is given', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }))

    await getJson('/some/path')

    const [, init] = fetchSpy.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBeNull()
  })

  it('attaches an Authorization header to a POST when a token is given', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }))

    await postJson('/some/path', { a: 1 }, undefined, 'a.jwt.token')

    const [, init] = fetchSpy.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBe('Bearer a.jwt.token')
  })
})
