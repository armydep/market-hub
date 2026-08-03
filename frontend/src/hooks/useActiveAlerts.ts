import { useQuery } from '@tanstack/react-query'
import { fetchActiveAlerts } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useActiveAlerts() {
  const token = useAuthStore((s) => s.token)
  return useQuery({
    queryKey: ['active-alerts'],
    queryFn: ({ signal }) => fetchActiveAlerts(token as string, signal),
    enabled: token !== null,
  })
}
