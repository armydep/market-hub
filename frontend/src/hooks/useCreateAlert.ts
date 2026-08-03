import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createAlert, type AlertInput } from '../api/client'
import type { ApiError, PriceAlert } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useCreateAlert() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<PriceAlert, ApiError, AlertInput>({
    mutationFn: (input) => createAlert(input, token as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['active-alerts'] }),
  })
}
