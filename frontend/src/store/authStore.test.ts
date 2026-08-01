import { describe, expect, it } from 'vitest'
import { isAuthenticated } from './authStore'

describe('isAuthenticated', () => {
  it('is false when there is no token', () => {
    expect(isAuthenticated({ token: null })).toBe(false)
  })

  it('is true once a token is present', () => {
    expect(isAuthenticated({ token: 'a.jwt.token' })).toBe(true)
  })
})
