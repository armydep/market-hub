import { useState } from 'react'
import { useRequestPasswordReset } from '../hooks/useRequestPasswordReset'
import styles from './ForgotPasswordPage.module.css'

export function ForgotPasswordPage() {
  const requestReset = useRequestPasswordReset()
  const [email, setEmail] = useState('')

  function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    requestReset.mutate(email)
  }

  return (
    <section className={styles.page}>
      <h1 className={styles.title}>Forgot password</h1>
      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.label} htmlFor="forgot-password-email">
          Email
        </label>
        <input
          id="forgot-password-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        {/* Whatever the server returns is shown verbatim: the no-enumeration
            guarantee (same message whether or not the email is registered)
            is already enforced server-side — the client just displays it. */}
        {requestReset.data && (
          <p className={styles.confirmation} role="status">
            {requestReset.data.message}
          </p>
        )}

        {requestReset.isError && (
          <p className={styles.error} role="alert">
            Something went wrong. Please try again.
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={requestReset.isPending}>
          {requestReset.isPending ? 'Sending…' : 'Send reset link'}
        </button>
      </form>
    </section>
  )
}
