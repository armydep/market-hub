import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse } from '../api/types'

interface AuthState {
  token: string | null
  userId: number | null
  email: string | null
  role: AuthResponse['role'] | null
  signIn: (response: AuthResponse) => void
  signOut: () => void
}

const SIGNED_OUT = { token: null, userId: null, email: null, role: null } as const

/**
 * Registered-user identity, persisted client-side (mirrors columnsStore.ts's
 * shape). A JWT is stateless by design (constraints.md), so sign-out is
 * purely local — there is no server session to invalidate.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      ...SIGNED_OUT,
      signIn: (response) =>
        set({
          token: response.token,
          userId: response.userId,
          email: response.email,
          role: response.role,
        }),
      signOut: () => set(SIGNED_OUT),
    }),
    { name: 'market-hub.auth' },
  ),
)

/** Pure derivation, the one directly unit-testable piece (see columnsStore.test.ts's convention). */
export function isAuthenticated(state: Pick<AuthState, 'token'>): boolean {
  return state.token !== null
}
