import { useQuery } from '@tanstack/react-query'
import { fetchTriggeredAlerts } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useTriggeredAlerts() {
  const token = useAuthStore((s) => s.token)
  return useQuery({
    queryKey: ['triggered-alerts'],
    queryFn: ({ signal }) => fetchTriggeredAlerts(token as string, signal),
    enabled: token !== null,
  })
}
