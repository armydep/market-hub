import { useQuery } from '@tanstack/react-query'
import { fetchAccount } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useAccount() {
  const token = useAuthStore((s) => s.token)
  return useQuery({
    queryKey: ['account'],
    queryFn: ({ signal }) => fetchAccount(token as string, signal),
    enabled: token !== null,
  })
}
