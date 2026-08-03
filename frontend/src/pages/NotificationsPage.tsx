import { conditionLabel } from '../alertConditions'
import { EmptyState, FatalError, LoadingState } from '../components/States'
import { useClearNotification } from '../hooks/useClearNotification'
import { useNotifications } from '../hooks/useNotifications'
import styles from './NotificationsPage.module.css'

/** In-app alert-trigger notifications (PRD F-007). Registered users only. */
export function NotificationsPage() {
  const notifications = useNotifications()
  const clearNotification = useClearNotification()

  if (notifications.isLoading) {
    return <LoadingState label="Loading notifications…" />
  }
  if (notifications.isError || !notifications.data) {
    return (
      <FatalError
        message={(notifications.error as Error)?.message ?? 'Could not load notifications.'}
        onRetry={() => notifications.refetch()}
      />
    )
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Notifications</h1>
      </header>

      {notifications.data.length === 0 ? (
        <EmptyState title="No notifications" />
      ) : (
        <ul className={styles.list}>
          {notifications.data.map((notification) => (
            <li key={notification.id} className={styles.row}>
              <span className={styles.symbol}>{notification.symbol}</span>
              <span>
                {conditionLabel(notification.condition)} ${notification.targetPrice} — triggered
                at ${notification.triggeredPrice} on{' '}
                {new Date(notification.triggeredAt).toLocaleString()}
              </span>
              <button
                type="button"
                onClick={() => clearNotification.mutate(notification.id)}
                disabled={clearNotification.isPending}
              >
                Clear
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
