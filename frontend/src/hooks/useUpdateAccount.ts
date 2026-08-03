import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateAccount, type UpdateAccountInput } from '../api/client'
import type { Account, ApiError } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useUpdateAccount() {
  const token = useAuthStore((s) => s.token)
  const updateEmail = useAuthStore((s) => s.updateEmail)
  const queryClient = useQueryClient()
  return useMutation<Account, ApiError, UpdateAccountInput>({
    mutationFn: (input) => updateAccount(input, token as string),
    onSuccess: (account) => {
      updateEmail(account.email)
      queryClient.setQueryData(['account'], account)
    },
  })
}
