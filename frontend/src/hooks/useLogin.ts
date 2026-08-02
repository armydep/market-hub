import { useMutation } from '@tanstack/react-query'
import { login, type Credentials } from '../api/client'
import type { ApiError, AuthResponse } from '../api/types'
import { useAuthStore } from '../store/authStore'

export function useLogin() {
  const signIn = useAuthStore((s) => s.signIn)
  return useMutation<AuthResponse, ApiError, Credentials>({
    mutationFn: (credentials) => login(credentials),
    onSuccess: signIn,
  })
}
