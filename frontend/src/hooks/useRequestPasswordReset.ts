import { useMutation } from '@tanstack/react-query'
import { requestPasswordReset } from '../api/client'
import type { ApiError, PasswordResetResponse } from '../api/types'

export function useRequestPasswordReset() {
  return useMutation<PasswordResetResponse, ApiError, string>({
    mutationFn: (email) => requestPasswordReset(email),
  })
}
