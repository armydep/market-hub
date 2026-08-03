import { useMutation, useQueryClient } from '@tanstack/react-query'
import { blockUser } from '../api/client'
import type { AdminUser, ApiError } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useBlockUser() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<AdminUser, ApiError, number>({
    mutationFn: (id) => blockUser(id, token as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}
