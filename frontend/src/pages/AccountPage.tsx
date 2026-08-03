import { useState } from 'react'
import { ApiError } from '../api/types'
import { FatalError, LoadingState } from '../components/States'
import { useAccount } from '../hooks/useAccount'
import { useChangePassword } from '../hooks/useChangePassword'
import { useUpdateAccount } from '../hooks/useUpdateAccount'
import styles from './AccountPage.module.css'

const MIN_PASSWORD_LENGTH = 8

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/** View/edit the caller's own account and change password (PRD F-009). */
export function AccountPage() {
  const account = useAccount()
  const updateAccount = useUpdateAccount()
  const changePassword = useChangePassword()

  const [email, setEmail] = useState('')
  const [emailCurrentPassword, setEmailCurrentPassword] = useState('')

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordTooShort, setPasswordTooShort] = useState(false)

  if (account.isLoading) {
    return <LoadingState label="Loading account…" />
  }
  if (account.isError || !account.data) {
    return (
      <FatalError
        message={(account.error as Error)?.message ?? 'Could not load your account.'}
        onRetry={() => account.refetch()}
      />
    )
  }

  function onEmailSubmit(event: React.FormEvent) {
    event.preventDefault()
    updateAccount.mutate(
      { email, currentPassword: emailCurrentPassword },
      { onSuccess: () => setEmailCurrentPassword('') },
    )
  }

  function onPasswordSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setPasswordTooShort(true)
      return
    }
    setPasswordTooShort(false)
    changePassword.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrentPassword('')
          setNewPassword('')
        },
      },
    )
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Account</h1>
      </header>

      <dl className={styles.summary}>
        <dt>Email</dt>
        <dd>{account.data.email}</dd>
        <dt>Role</dt>
        <dd>{account.data.role}</dd>
        <dt>Registered</dt>
        <dd>{new Date(account.data.createdAt).toLocaleDateString()}</dd>
      </dl>

      <form className={styles.form} onSubmit={onEmailSubmit}>
        <h2 className={styles.sectionTitle}>Change email</h2>
        <label className={styles.label} htmlFor="account-new-email">
          New email
        </label>
        <input
          id="account-new-email"
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        <label className={styles.label} htmlFor="account-email-current-password">
          Current password
        </label>
        <input
          id="account-email-current-password"
          type="password"
          required
          value={emailCurrentPassword}
          onChange={(event) => setEmailCurrentPassword(event.target.value)}
        />

        {updateAccount.isSuccess && (
          <p className={styles.confirmation} role="status">
            Your email has been updated.
          </p>
        )}
        {updateAccount.isError && (
          <p className={styles.error} role="alert">
            {errorMessage(updateAccount.error, 'Could not update your email. Please try again.')}
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={updateAccount.isPending}>
          {updateAccount.isPending ? 'Saving…' : 'Save email'}
        </button>
      </form>

      <form className={styles.form} onSubmit={onPasswordSubmit}>
        <h2 className={styles.sectionTitle}>Change password</h2>
        <label className={styles.label} htmlFor="account-current-password">
          Current password
        </label>
        <input
          id="account-current-password"
          type="password"
          required
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
        />

        <label className={styles.label} htmlFor="account-new-password">
          New password
        </label>
        <input
          id="account-new-password"
          type="password"
          required
          minLength={8}
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />

        {passwordTooShort && (
          <p className={styles.error} role="alert">
            Password must be at least {MIN_PASSWORD_LENGTH} characters.
          </p>
        )}
        {changePassword.isSuccess && (
          <p className={styles.confirmation} role="status">
            Your password has been changed.
          </p>
        )}
        {changePassword.isError && (
          <p className={styles.error} role="alert">
            {errorMessage(changePassword.error, 'Could not change your password. Please try again.')}
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={changePassword.isPending}>
          {changePassword.isPending ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </section>
  )
}
