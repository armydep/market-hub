import { useMutation } from '@tanstack/react-query'
import { login, type Credentials } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useLogin() {
  const signIn = useAuthStore((s) => s.signIn)
  return useMutation({
    mutationFn: (credentials: Credentials) => login(credentials),
    onSuccess: signIn,
  })
}
