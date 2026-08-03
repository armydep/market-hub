import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateAccountPreferences } from '../api/client'
import type { AccountPreferences, ApiError } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useUpdateAccountPreferences() {
  const token = useAuthStore((s) => s.token)
  const queryClient = useQueryClient()
  return useMutation<AccountPreferences, ApiError, string[]>({
    mutationFn: (visibleColumns) => updateAccountPreferences(visibleColumns, token as string),
    onSuccess: (preferences) => queryClient.setQueryData(['account-preferences'], preferences),
  })
}
