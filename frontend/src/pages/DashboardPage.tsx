import { useCallback } from 'react'
import { CoinGrid } from '../components/CoinGrid'
import { DashboardToolbar } from '../components/DashboardToolbar'
import { Pagination } from '../components/Pagination'
import { EmptyState, FatalError, LoadingState, RefreshFailureBanner } from '../components/States'
import { useCoins } from '../hooks/useCoins'
import { useColumnCatalog } from '../hooks/useColumnCatalog'
import { useDashboardParams } from '../hooks/useDashboardParams'
import { resolveVisibleColumns, useColumnsStore } from '../store/columnsStore'
import styles from './DashboardPage.module.css'

export function DashboardPage() {
  const catalog = useColumnCatalog()
  // Until the catalog loads there's no server default to fall back on; 20 is the
  // PRD-fixed default. The coin query stays disabled until the catalog resolves,
  // so no request is ever sent carrying this placeholder.
  const { params, update, updateAndResetPage } = useDashboardParams(
    catalog.data?.defaultPageSize ?? 20,
    catalog.data?.supportedPageSizes,
  )
  const coins = useCoins(params, catalog.isSuccess)

  const chosenColumns = useColumnsStore((s) => s.visibleColumns)
  const toggleColumn = useColumnsStore((s) => s.toggleColumn)

  // Stable identities: DashboardToolbar debounces the search and lists its
  // handler in the effect deps, so an inline arrow would restart the 300ms
  // timer on every re-render of this page (isFetching alone flips twice per
  // refetch) and could defer the URL update indefinitely while typing.
  const onSearchChange = useCallback(
    (q: string) => updateAndResetPage({ q }),
    [updateAndResetPage],
  )
  const onPageSizeChange = useCallback(
    (size: number) => updateAndResetPage({ size }),
    [updateAndResetPage],
  )
  const onPageChange = useCallback((next: number) => update({ page: next }), [update])

  if (catalog.isLoading) {
    return <LoadingState label="Loading dashboard…" />
  }
  if (catalog.isError || !catalog.data) {
    // Without the catalog there are no columns to render, so this one is fatal
    // in a way a failed coin refresh is not.
    return (
      <FatalError
        message={(catalog.error as Error)?.message ?? 'Could not load the column catalog.'}
        onRetry={() => catalog.refetch()}
      />
    )
  }

  const { supported, defaultVisible, supportedPageSizes } = catalog.data
  const visibleColumns = resolveVisibleColumns(chosenColumns, defaultVisible, supported)

  const page = coins.data
  const hasRows = (page?.content.length ?? 0) > 0
  const isSearching = params.q.trim() !== ''

  const onSortChange = (column: string) => {
    const nextOrder = params.sort === column && params.order === 'asc' ? 'desc' : 'asc'
    updateAndResetPage({ sort: column, order: nextOrder })
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Market Hub</h1>
        <p className={styles.subtitle}>
          Top cryptocurrencies by market capitalization. All values in USD.
        </p>
      </header>

      <DashboardToolbar
        search={params.q}
        onSearchChange={onSearchChange}
        pageSize={params.size}
        supportedPageSizes={supportedPageSizes}
        onPageSizeChange={onPageSizeChange}
        supportedColumns={supported}
        visibleColumns={visibleColumns}
        onToggleColumn={(key) => toggleColumn(key, defaultVisible)}
        lastUpdatedAt={page?.lastUpdatedAt ?? null}
        isFetching={coins.isFetching}
        onRefresh={() => coins.refetch()}
      />

      {/* Rendered above the grid, not instead of it: a failed refresh must keep
          the last good rows on screen (F001-FR-020). */}
      {coins.isError && hasRows && (
        <RefreshFailureBanner message={(coins.error as Error)?.message ?? ''} />
      )}

      {coins.isLoading && !hasRows && <LoadingState label="Loading market data…" />}

      {coins.isError && !hasRows && (
        <FatalError
          message={(coins.error as Error)?.message ?? 'Could not load market data.'}
          onRetry={() => coins.refetch()}
        />
      )}

      {!coins.isLoading && !hasRows && !coins.isError && (
        <EmptyState
          title={isSearching ? 'No results found' : 'No market data yet'}
          hint={
            isSearching
              ? 'Try a different name or symbol, or clear the search.'
              : 'The universe is empty — the poller may not have run yet.'
          }
        />
      )}

      {hasRows && page && (
        <>
          <CoinGrid
            coins={page.content}
            visibleColumns={visibleColumns}
            sort={params.sort}
            order={params.order}
            onSortChange={onSortChange}
          />
          <Pagination
            page={page.page}
            totalPages={page.totalPages}
            totalElements={page.totalElements}
            onPageChange={onPageChange}
          />
        </>
      )}
    </section>
  )
}
