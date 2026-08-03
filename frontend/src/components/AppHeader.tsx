import { Link } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import styles from './AppHeader.module.css'

/**
 * The only piece of persistent chrome in the app. Not itemized in the S5
 * spec's file list, but necessary: without some persistent element, "sign
 * out" (an explicit acceptance criterion) has no click target anywhere.
 */
export function AppHeader() {
  const email = useAuthStore((s) => s.email)
  const role = useAuthStore((s) => s.role)
  const signOut = useAuthStore((s) => s.signOut)

  return (
    <header className={styles.header}>
      <Link to="/" className={styles.brand}>
        Market Hub
      </Link>
      <nav className={styles.nav}>
        {email ? (
          <>
            {role === 'ADMIN' && <Link to="/admin/users">Admin</Link>}
            <span className={styles.signedInAs}>Signed in as {email}</span>
            <button type="button" className={styles.signOut} onClick={signOut}>
              Sign out
            </button>
          </>
        ) : (
          <>
            <Link to="/sign-in">Sign in</Link>
            <Link to="/register">Register</Link>
          </>
        )}
      </nav>
    </header>
  )
}
