import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useConfirmPasswordReset } from '../hooks/useConfirmPasswordReset'
import styles from './ResetPasswordPage.module.css'

const MIN_PASSWORD_LENGTH = 8

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const confirmReset = useConfirmPasswordReset()
  const [newPassword, setNewPassword] = useState('')
  const [passwordTooShort, setPasswordTooShort] = useState(false)

  function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    // Explicit check, not just the input's minLength attribute: jsdom doesn't
    // enforce HTML5 constraint validation on submit (see RegisterPage).
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setPasswordTooShort(true)
      return
    }
    setPasswordTooShort(false)
    confirmReset.mutate({ token, newPassword })
  }

  if (confirmReset.isSuccess) {
    return (
      <section className={styles.page}>
        <h1 className={styles.title}>Reset password</h1>
        <p className={styles.confirmation} role="status">
          {confirmReset.data.message}
        </p>
        <Link to="/sign-in">Sign in</Link>
      </section>
    )
  }

  return (
    <section className={styles.page}>
      <h1 className={styles.title}>Reset password</h1>
      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.label} htmlFor="reset-password-new-password">
          New password
        </label>
        <input
          id="reset-password-new-password"
          type="password"
          required
          minLength={MIN_PASSWORD_LENGTH}
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />

        {passwordTooShort && (
          <p className={styles.error} role="alert">
            Password must be at least {MIN_PASSWORD_LENGTH} characters.
          </p>
        )}

        {confirmReset.isError && (
          <p className={styles.error} role="alert">
            {confirmReset.error.message}
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={confirmReset.isPending}>
          {confirmReset.isPending ? 'Resetting…' : 'Reset password'}
        </button>
      </form>
    </section>
  )
}
