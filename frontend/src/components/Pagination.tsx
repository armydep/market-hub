import styles from './Pagination.module.css'

interface Props {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
  /** What totalElements counts, for the summary line. Defaults to the dashboard's original wording. */
  unit?: string
}

export function Pagination({ page, totalPages, totalElements, onPageChange, unit = 'coins' }: Props) {
  const isFirst = page <= 0
  const isLast = totalPages === 0 || page >= totalPages - 1

  return (
    <nav className={styles.pagination} aria-label="Pagination">
      <span className={styles.summary} data-testid="page-summary">
        {totalElements === 0
          ? 'No results'
          : `Page ${page + 1} of ${totalPages} · ${totalElements} ${unit}`}
      </span>
      <div className={styles.controls}>
        <button type="button" onClick={() => onPageChange(0)} disabled={isFirst}>
          « First
        </button>
        <button type="button" onClick={() => onPageChange(page - 1)} disabled={isFirst}>
          ‹ Previous
        </button>
        <button type="button" onClick={() => onPageChange(page + 1)} disabled={isLast}>
          Next ›
        </button>
        <button type="button" onClick={() => onPageChange(totalPages - 1)} disabled={isLast}>
          Last »
        </button>
      </div>
    </nav>
  )
}
