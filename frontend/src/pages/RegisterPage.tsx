import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/types'
import { useRegister } from '../hooks/useRegister'
import styles from './RegisterPage.module.css'

export function RegisterPage() {
  const navigate = useNavigate()
  const register = useRegister()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    register.mutate(
      { email, password },
      { onSuccess: () => navigate('/') },
    )
  }

  return (
    <section className={styles.page}>
      <h1 className={styles.title}>Register</h1>
      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.label} htmlFor="register-email">
          Email
        </label>
        <input
          id="register-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        <label className={styles.label} htmlFor="register-password">
          Password
        </label>
        <input
          id="register-password"
          type="password"
          required
          minLength={8}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {register.isError && (
          <p className={styles.error} role="alert">
            {register.error instanceof ApiError && register.error.status === 409
              ? 'That email is already registered.'
              : 'Could not register. Please try again.'}
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={register.isPending}>
          {register.isPending ? 'Registering…' : 'Register'}
        </button>
      </form>
    </section>
  )
}
