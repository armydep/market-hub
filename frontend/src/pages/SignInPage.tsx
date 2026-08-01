import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLogin } from '../hooks/useLogin'
import styles from './SignInPage.module.css'

export function SignInPage() {
  const navigate = useNavigate()
  const login = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    login.mutate(
      { email, password },
      { onSuccess: () => navigate('/') },
    )
  }

  return (
    <section className={styles.page}>
      <h1 className={styles.title}>Sign in</h1>
      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.label} htmlFor="sign-in-email">
          Email
        </label>
        <input
          id="sign-in-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        <label className={styles.label} htmlFor="sign-in-password">
          Password
        </label>
        <input
          id="sign-in-password"
          type="password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {login.isError && (
          <p className={styles.error} role="alert">
            Invalid email or password.
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={login.isPending}>
          {login.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </section>
  )
}
