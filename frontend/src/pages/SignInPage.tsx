import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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

        {login.error && (
          <p className={styles.error} role="alert">
            {/* 401 (wrong/unknown credentials) always shows this fixed generic
                copy, never revealing which part was wrong. 403 (blocked or
                temporarily locked) shows the backend's own distinct message. */}
            {login.error.status === 401 ? 'Invalid email or password.' : login.error.message}
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={login.isPending}>
          {login.isPending ? 'Signing in…' : 'Sign in'}
        </button>

        <Link to="/forgot-password" className={styles.forgotPassword}>
          Forgot password?
        </Link>
      </form>
    </section>
  )
}
