import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateAlert, type AlertUpdateInput } from '../api/client'
import type { ApiError, PriceAlert } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useUpdateAlert() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<PriceAlert, ApiError, { id: number; body: AlertUpdateInput }>({
    mutationFn: ({ id, body }) => updateAlert(id, body, token as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['active-alerts'] }),
  })
}
