import { useMutation } from '@tanstack/react-query'
import { registerUser, type Credentials } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function useRegister() {
  const signIn = useAuthStore((s) => s.signIn)
  return useMutation({
    mutationFn: (credentials: Credentials) => registerUser(credentials),
    onSuccess: signIn,
  })
}
