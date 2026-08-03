import { useState } from 'react'
import type { AlertCondition } from '../api/types'
import { ApiError, type PriceAlert } from '../api/types'
import { EmptyState, FatalError, LoadingState } from '../components/States'
import { useActiveAlerts } from '../hooks/useActiveAlerts'
import { useClearAlert } from '../hooks/useClearAlert'
import { useCreateAlert } from '../hooks/useCreateAlert'
import { useDeleteAlert } from '../hooks/useDeleteAlert'
import { useTriggeredAlerts } from '../hooks/useTriggeredAlerts'
import { useUpdateAlert } from '../hooks/useUpdateAlert'
import styles from './AlertsPage.module.css'

const CONDITIONS: { value: AlertCondition; label: string }[] = [
  { value: 'ABOVE_OR_EQUAL', label: 'At or above' },
  { value: 'BELOW_OR_EQUAL', label: 'At or below' },
]

function conditionLabel(condition: AlertCondition): string {
  return CONDITIONS.find((c) => c.value === condition)?.label ?? condition
}

/** One-time above/below price alerts (PRD F-006). Registered users only. */
export function AlertsPage() {
  const active = useActiveAlerts()
  const triggered = useTriggeredAlerts()
  const createAlert = useCreateAlert()
  const updateAlert = useUpdateAlert()
  const deleteAlert = useDeleteAlert()
  const clearAlert = useClearAlert()

  const [symbol, setSymbol] = useState('')
  const [condition, setCondition] = useState<AlertCondition>('ABOVE_OR_EQUAL')
  const [targetPrice, setTargetPrice] = useState('')

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editCondition, setEditCondition] = useState<AlertCondition>('ABOVE_OR_EQUAL')
  const [editTargetPrice, setEditTargetPrice] = useState('')

  if (active.isLoading || triggered.isLoading) {
    return <LoadingState label="Loading alerts…" />
  }
  if (active.isError || !active.data) {
    return (
      <FatalError
        message={(active.error as Error)?.message ?? 'Could not load alerts.'}
        onRetry={() => active.refetch()}
      />
    )
  }
  if (triggered.isError || !triggered.data) {
    return (
      <FatalError
        message={(triggered.error as Error)?.message ?? 'Could not load alerts.'}
        onRetry={() => triggered.refetch()}
      />
    )
  }

  function onCreateSubmit(event: React.FormEvent) {
    event.preventDefault()
    createAlert.mutate(
      { symbol: symbol.trim().toUpperCase(), condition, targetPrice: Number(targetPrice) },
      {
        onSuccess: () => {
          setSymbol('')
          setTargetPrice('')
        },
      },
    )
  }

  function startEdit(alert: PriceAlert) {
    setEditingId(alert.id)
    setEditCondition(alert.condition)
    setEditTargetPrice(String(alert.targetPrice))
  }

  function onEditSubmit(event: React.FormEvent, id: number) {
    event.preventDefault()
    updateAlert.mutate(
      { id, body: { condition: editCondition, targetPrice: Number(editTargetPrice) } },
      { onSuccess: () => setEditingId(null) },
    )
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Price Alerts</h1>
      </header>

      <form className={styles.form} onSubmit={onCreateSubmit}>
        <label className={styles.label} htmlFor="alert-symbol">
          Symbol
        </label>
        <input
          id="alert-symbol"
          required
          placeholder="BTC"
          value={symbol}
          onChange={(event) => setSymbol(event.target.value)}
        />

        <label className={styles.label} htmlFor="alert-condition">
          Condition
        </label>
        <select
          id="alert-condition"
          value={condition}
          onChange={(event) => setCondition(event.target.value as AlertCondition)}
        >
          {CONDITIONS.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>

        <label className={styles.label} htmlFor="alert-target-price">
          Target price (USD)
        </label>
        <input
          id="alert-target-price"
          type="number"
          required
          min="0"
          step="any"
          value={targetPrice}
          onChange={(event) => setTargetPrice(event.target.value)}
        />

        {createAlert.isError && (
          <p className={styles.error} role="alert">
            {createAlert.error instanceof ApiError
              ? createAlert.error.message
              : 'Could not create alert. Please try again.'}
          </p>
        )}

        <button type="submit" className={styles.submit} disabled={createAlert.isPending}>
          {createAlert.isPending ? 'Creating…' : 'Create alert'}
        </button>
      </form>

      <div>
        <h2 className={styles.sectionTitle}>Active alerts</h2>
        {active.data.length === 0 ? (
          <EmptyState title="No active alerts" />
        ) : (
          <ul className={styles.list}>
            {active.data.map((alert) => (
              <li key={alert.id} className={styles.row}>
                {editingId === alert.id ? (
                  <form className={styles.inlineForm} onSubmit={(event) => onEditSubmit(event, alert.id)}>
                    <span className={styles.symbol}>{alert.symbol}</span>
                    <select
                      value={editCondition}
                      onChange={(event) => setEditCondition(event.target.value as AlertCondition)}
                    >
                      {CONDITIONS.map((c) => (
                        <option key={c.value} value={c.value}>
                          {c.label}
                        </option>
                      ))}
                    </select>
                    <input
                      type="number"
                      required
                      min="0"
                      step="any"
                      value={editTargetPrice}
                      onChange={(event) => setEditTargetPrice(event.target.value)}
                    />
                    <button type="submit" disabled={updateAlert.isPending}>
                      Save
                    </button>
                    <button type="button" onClick={() => setEditingId(null)}>
                      Cancel
                    </button>
                    {updateAlert.isError && (
                      <p className={styles.error} role="alert">
                        {updateAlert.error instanceof ApiError
                          ? updateAlert.error.message
                          : 'Could not update alert.'}
                      </p>
                    )}
                  </form>
                ) : (
                  <>
                    <span className={styles.symbol}>{alert.symbol}</span>
                    <span>
                      {conditionLabel(alert.condition)} ${alert.targetPrice}
                    </span>
                    <button type="button" onClick={() => startEdit(alert)}>
                      Edit
                    </button>
                    <button
                      type="button"
                      onClick={() => deleteAlert.mutate(alert.id)}
                      disabled={deleteAlert.isPending}
                    >
                      Delete
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2 className={styles.sectionTitle}>Triggered alerts</h2>
        {triggered.data.length === 0 ? (
          <EmptyState title="No triggered alerts" />
        ) : (
          <ul className={styles.list}>
            {triggered.data.map((alert) => (
              <li key={alert.id} className={styles.row}>
                <span className={styles.symbol}>{alert.symbol}</span>
                <span>
                  {conditionLabel(alert.condition)} ${alert.targetPrice} — triggered at $
                  {alert.triggeredPrice} on{' '}
                  {alert.triggeredAt ? new Date(alert.triggeredAt).toLocaleString() : ''}
                </span>
                <button
                  type="button"
                  onClick={() => clearAlert.mutate(alert.id)}
                  disabled={clearAlert.isPending}
                >
                  Clear
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
