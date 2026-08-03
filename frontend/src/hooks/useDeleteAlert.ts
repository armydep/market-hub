import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteAlert } from '../api/client'
import type { ApiError } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useDeleteAlert() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<void, ApiError, number>({
    mutationFn: (id) => deleteAlert(id, token as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['active-alerts'] }),
  })
}
