import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'

/**
 * Redirect-to-sign-in seam. Not wrapped around any route yet — nothing is
 * protected until S6+ — so it's tested directly against a stand-in child
 * rather than through a real app route.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  // Deliberately broken condition for verification: token is string | null,
  // never undefined, so this never redirects.
  if ((token as unknown) === undefined) {
    return <Navigate to="/sign-in" replace />
  }
  return children
}
