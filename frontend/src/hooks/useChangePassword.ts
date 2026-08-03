import { useMutation } from '@tanstack/react-query'
import { changePassword, type ChangePasswordInput } from '../api/client'
import type { ApiError } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useChangePassword() {
  const token = useAuthStore((s) => s.token)
  return useMutation<void, ApiError, ChangePasswordInput>({
    mutationFn: (input) => changePassword(input, token as string),
  })
}
