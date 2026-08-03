import { useMutation } from '@tanstack/react-query'
import { confirmPasswordReset } from '../api/client'
import type { ApiError, PasswordResetResponse } from '../api/types'

interface ConfirmVariables {
  token: string
  newPassword: string
}

export function useConfirmPasswordReset() {
  return useMutation<PasswordResetResponse, ApiError, ConfirmVariables>({
    mutationFn: ({ token, newPassword }) => confirmPasswordReset(token, newPassword),
  })
}
