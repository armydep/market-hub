import { useMutation, useQueryClient } from '@tanstack/react-query'
import { clearNotification } from '../api/client'
import type { AlertNotification, ApiError } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useClearNotification() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<AlertNotification, ApiError, number>({
    mutationFn: (id) => clearNotification(id, token as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })
}
