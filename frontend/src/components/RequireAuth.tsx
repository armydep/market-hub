import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import type { AuthResponse } from '../api/types'
import { useAuthStore } from '../store/authStore'
import { EmptyState } from './States'

interface Props {
  children: ReactNode
  /** When set, only this exact role may view the content (e.g. "ADMIN"). */
  role?: AuthResponse['role']
}

/**
 * Redirect-to-sign-in seam, now wrapping the S11 admin route. An
 * authenticated-but-wrong-role visitor sees an inline "Access denied" state
 * rather than a redirect, since they *are* signed in — there's nowhere more
 * useful to send them. This is client-side defense in depth only; the real
 * gate is the backend's `@PreAuthorize` (constraints.md: "UI hiding is not
 * an authorization control").
 */
export function RequireAuth({ children, role }: Props) {
  const token = useAuthStore((s) => s.token)
  const userRole = useAuthStore((s) => s.role)
  if (token === null) {
    return <Navigate to="/sign-in" replace />
  }
  if (role && userRole !== role) {
    return <EmptyState title="Access denied" hint="You don't have permission to view this page." />
  }
  return children
}
