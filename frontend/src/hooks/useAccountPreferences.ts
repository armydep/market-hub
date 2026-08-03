import { useQuery } from '@tanstack/react-query'
import { fetchAccountPreferences } from '../api/client'
import { useAuthStore } from '../store/authStore'

/** Server-persisted visible-column set for a registered user (S8). Guests never call this. */
export function useAccountPreferences() {
  const token = useAuthStore((s) => s.token)
  return useQuery({
    queryKey: ['account-preferences'],
    queryFn: ({ signal }) => fetchAccountPreferences(token as string, signal),
    enabled: token !== null,
  })
}
