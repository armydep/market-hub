import { useEffect, useState } from 'react'
import { formatTimestamp } from '../format'
import { ColumnPicker } from './ColumnPicker'
import styles from './DashboardToolbar.module.css'

interface Props {
  search: string
  onSearchChange: (value: string) => void
  pageSize: number
  supportedPageSizes: number[]
  onPageSizeChange: (size: number) => void
  supportedColumns: string[]
  visibleColumns: string[]
  onToggleColumn: (key: string) => void
  lastUpdatedAt: string | null
  isFetching: boolean
  onRefresh: () => void
}

export function DashboardToolbar({
  search,
  onSearchChange,
  pageSize,
  supportedPageSizes,
  onPageSizeChange,
  supportedColumns,
  visibleColumns,
  onToggleColumn,
  lastUpdatedAt,
  isFetching,
  onRefresh,
}: Props) {
  // Local mirror so typing feels immediate; the URL (the source of truth) is
  // updated on a debounce so every keystroke isn't a request and a history entry.
  const [draft, setDraft] = useState(search)

  useEffect(() => {
    setDraft(search)
  }, [search])

  useEffect(() => {
    if (draft === search) return
    const timer = setTimeout(() => onSearchChange(draft), 300)
    return () => clearTimeout(timer)
  }, [draft, search, onSearchChange])

  return (
    <div className={styles.toolbar}>
      <div className={styles.searchGroup}>
        <label className={styles.label} htmlFor="coin-search">
          Search
        </label>
        <input
          id="coin-search"
          type="search"
          className={styles.search}
          placeholder="Name or symbol"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
        />
      </div>

      <div className={styles.group}>
        <label className={styles.label} htmlFor="page-size">
          Rows
        </label>
        <select
          id="page-size"
          className={styles.select}
          value={pageSize}
          onChange={(event) => onPageSizeChange(Number(event.target.value))}
        >
          {supportedPageSizes.map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
      </div>

      <ColumnPicker
        supported={supportedColumns}
        visible={visibleColumns}
        onToggle={onToggleColumn}
      />

      <div className={styles.spacer} />

      <div className={styles.freshness}>
        <span className={styles.label}>Last updated</span>
        <span data-testid="last-updated">{formatTimestamp(lastUpdatedAt)}</span>
      </div>

      <button type="button" className={styles.refresh} onClick={onRefresh} disabled={isFetching}>
        {isFetching ? 'Refreshing…' : 'Refresh'}
      </button>
    </div>
  )
}
