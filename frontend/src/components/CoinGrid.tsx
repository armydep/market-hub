import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from '@tanstack/react-table'
import { useMemo } from 'react'
import type { Coin } from '../api/types'
import { columnLabel, formatCompactUsd, formatNumber, formatPercent, formatUsd } from '../format'
import styles from './CoinGrid.module.css'

interface Props {
  coins: Coin[]
  visibleColumns: string[]
  sort: string
  order: 'asc' | 'desc'
  onSortChange: (column: string) => void
}

function renderCell(coin: Coin, key: string) {
  switch (key) {
    case 'marketCapRank':
      return coin.marketCapRank ?? '—'
    case 'name':
      return coin.name
    case 'symbol':
      return coin.symbol
    case 'price':
      return formatUsd(coin.price)
    case 'pctChange1h':
      return formatPercent(coin.pctChange1h)
    case 'pctChange24h':
      return formatPercent(coin.pctChange24h)
    case 'pctChange7d':
      return formatPercent(coin.pctChange7d)
    case 'marketCap':
      return formatCompactUsd(coin.marketCap)
    case 'volume24h':
      return formatCompactUsd(coin.volume24h)
    case 'circulatingSupply':
      return formatNumber(coin.circulatingSupply)
    default:
      return '—'
  }
}

function percentClass(value: number | null): string | undefined {
  if (value === null) return undefined
  return value >= 0 ? styles.up : styles.down
}

export function CoinGrid({ coins, visibleColumns, sort, order, onSortChange }: Props) {
  const columns = useMemo<ColumnDef<Coin>[]>(
    () =>
      visibleColumns.map((key) => ({
        id: key,
        header: () => columnLabel(key),
        cell: ({ row }) => {
          const value = renderCell(row.original, key)
          const isPercent = key.startsWith('pctChange')
          return (
            <span
              className={
                isPercent
                  ? percentClass(row.original[key as keyof Coin] as number | null)
                  : undefined
              }
            >
              {value}
            </span>
          )
        },
      })),
    [visibleColumns],
  )

  const table = useReactTable({
    data: coins,
    columns,
    getCoreRowModel: getCoreRowModel(),
    // The server owns sorting, filtering and paging. These flags are what stop
    // TanStack Table re-sorting the rows it currently holds — which would look
    // correct on page 1 and be wrong on every other page (F001-FR-011).
    manualPagination: true,
    manualSorting: true,
    manualFiltering: true,
  })

  return (
    <div className={styles.wrapper}>
      <table className={styles.table}>
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                const isSorted = header.column.id === sort
                return (
                  <th key={header.id} scope="col">
                    <button
                      type="button"
                      className={styles.sortButton}
                      onClick={() => onSortChange(header.column.id)}
                      aria-label={`Sort by ${columnLabel(header.column.id)}`}
                      aria-sort={
                        isSorted ? (order === 'asc' ? 'ascending' : 'descending') : 'none'
                      }
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      <span aria-hidden="true" className={styles.sortIndicator}>
                        {isSorted ? (order === 'asc' ? '▲' : '▼') : ''}
                      </span>
                    </button>
                  </th>
                )
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr key={row.id}>
              {row.getVisibleCells().map((cell) => (
                <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
