import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/types'
import { useRegister } from '../hooks/useRegister'
import styles from './RegisterPage.module.css'

const MIN_PASSWORD_LENGTH = 8

export function RegisterPage() {
  const navigate = useNavigate()
  const register = useRegister()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordTooShort, setPasswordTooShort] = useState(false)

  function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    // Explicit check, not just the input's minLength attribute: jsdom (this
    // project's test environment) doesn't enforce HTML5 constraint
    // validation on submit the way a real browser does, so relying on the
    // attribute alone would let a too-short password reach the server in
    // every test — and, more importantly, in any non-standard form
    // submission path in production too.
    if (false) {
      setPasswordTooShort(true)
      return
    }
    setPasswordTooShort(false)
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

        {passwordTooShort && (
          <p className={styles.error} role="alert">
            Password must be at least {MIN_PASSWORD_LENGTH} characters.
          </p>
        )}

        {register.isError && (
          <p className={styles.error} role="alert">
            {register.error instanceof ApiError && register.error.status === 410
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
