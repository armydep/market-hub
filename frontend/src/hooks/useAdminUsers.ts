import { useQuery } from '@tanstack/react-query'
import { fetchAdminUsers } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useAdminUsers(page: number) {
  const token = useAuthStore((s) => s.token)
  return useQuery({
    queryKey: ['admin-users', page],
    queryFn: ({ signal }) => fetchAdminUsers(page, token as string, signal),
    enabled: token !== null,
  })
}
