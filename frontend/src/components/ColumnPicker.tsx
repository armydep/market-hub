import { useEffect, useRef, useState } from 'react'
import { columnLabel } from '../format'
import styles from './ColumnPicker.module.css'

interface Props {
  supported: string[]
  visible: string[]
  onToggle: (key: string) => void
}

export function ColumnPicker({ supported, visible, onToggle }: Props) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClickAway = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onClickAway)
    document.addEventListener('keydown', onEscape)
    return () => {
      document.removeEventListener('mousedown', onClickAway)
      document.removeEventListener('keydown', onEscape)
    }
  }, [open])

  return (
    <div className={styles.container} ref={containerRef}>
      <span className={styles.label}>Columns</span>
      <button
        type="button"
        className={styles.trigger}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="true"
      >
        {visible.length} of {supported.length}
      </button>

      {open && (
        <div className={styles.menu} role="group" aria-label="Visible columns">
          {supported.map((key) => {
            const checked = visible.includes(key)
            return (
              <label key={key} className={styles.item}>
                <input
                  type="checkbox"
                  checked={checked}
                  // Never let the user hide every column — an empty grid looks
                  // identical to a broken one.
                  disabled={checked && visible.length === 1}
                  onChange={() => onToggle(key)}
                />
                {columnLabel(key)}
              </label>
            )
          })}
        </div>
      )}
    </div>
  )
}
