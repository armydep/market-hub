import { useMutation, useQueryClient } from '@tanstack/react-query'
import { clearAlert } from '../api/client'
import type { ApiError, PriceAlert } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useClearAlert() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<PriceAlert, ApiError, number>({
    mutationFn: (id) => clearAlert(id, token as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['triggered-alerts'] }),
  })
}
