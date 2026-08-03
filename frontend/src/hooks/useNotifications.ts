import { useQuery } from '@tanstack/react-query'
import { fetchNotifications } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useNotifications() {
  const token = useAuthStore((s) => s.token)
  return useQuery({
    queryKey: ['notifications'],
    queryFn: ({ signal }) => fetchNotifications(token as string, signal),
    enabled: token !== null,
  })
}
