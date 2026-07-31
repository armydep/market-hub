import styles from './States.module.css'

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className={styles.state} role="status">
      {label}
    </div>
  )
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className={styles.state}>
      <p className={styles.title}>{title}</p>
      {hint && <p className={styles.hint}>{hint}</p>}
    </div>
  )
}

/**
 * Shown alongside stale rows, never instead of them (F001-FR-020): when a
 * refresh fails after data has loaded, the last good data must stay on screen.
 */
export function RefreshFailureBanner({ message }: { message: string }) {
  return (
    <div className={styles.failure} role="alert">
      <strong>Couldn't refresh.</strong> {message} Showing the last data loaded.
    </div>
  )
}

export function FatalError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className={styles.fatal} role="alert">
      <p className={styles.title}>Something went wrong</p>
      <p className={styles.hint}>{message}</p>
      {onRetry && (
        <button type="button" className={styles.retry} onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}
