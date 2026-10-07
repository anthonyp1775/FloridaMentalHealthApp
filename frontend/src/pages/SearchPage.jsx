import { useCallback, useMemo, useReducer } from 'react'
import { Link } from 'react-router-dom'
import { catalogApi, providerApi } from '../api'
import { useFetch } from '../hooks'
import SearchFilters from '../components/SearchFilters'
import ProviderCard from '../components/ProviderCard'
import { Button, Empty, Notice, Spinner } from '../components/ui'

/**
 * The search page - the core of the application.
 *
 * FILTER STATE USES useReducer, not four useStates. The reason is the
 * rule in the 'set' and 'toggle' branches: changing any filter resets
 * the page to 0. With separate state variables that reset has to be
 * remembered at every call site, and the bug it causes - "no results"
 * because you are on page 3 of a narrower search - looks like a backend
 * problem. Here it is impossible to forget, because every filter change
 * goes through the same two lines.
 */

const initialFilters = {
  countyId: '',
  insurancePlanId: '',
  telehealth: false,
  acceptingOnly: false,
  page: 0
}

function filterReducer(state, action) {
  switch (action.type) {
    case 'set':
      return { ...state, [action.field]: action.value, page: 0 }
    case 'toggle':
      return { ...state, [action.field]: !state[action.field], page: 0 }
    case 'page':
      return { ...state, page: action.page }
    case 'reset':
      return initialFilters
    default:
      throw new Error(`Unknown filter action: ${action.type}`)
  }
}

export default function SearchPage() {
  const [filters, dispatch] = useReducer(filterReducer, initialFilters)

  // Reference data for the dropdowns. Loaded once - it is near-static.
  const catalog = useFetch(
    () => Promise.all([
      catalogApi.counties(),
      catalogApi.insurancePlans()
    ]).then(([counties, insurancePlans]) => ({ counties, insurancePlans })),
    []
  )

  /*
   * Drop empty filters before sending. The API treats an absent
   * parameter as "no constraint", so sending countyId='' would be a
   * different request than sending nothing at all.
   */
  const params = useMemo(() => {
    const out = { page: filters.page, size: 20 }
    for (const key of ['countyId', 'insurancePlanId']) {
      if (filters[key]) out[key] = filters[key]
    }
    if (filters.telehealth) out.telehealth = true
    if (filters.acceptingOnly) out.acceptingOnly = true
    return out
  }, [filters])

  /*
   * A stable string key for the dependency list. Depending on `params`
   * directly would refire on every render that rebuilt the object.
   */
  const paramsKey = useMemo(() => JSON.stringify(params), [params])

  const results = useFetch(() => providerApi.search(params), [paramsKey])

  const onChange = useCallback(
    (field, value) => dispatch({ type: 'set', field, value }), [])
  const onToggle = useCallback(
    (field) => dispatch({ type: 'toggle', field }), [])
  const onReset = useCallback(
    () => dispatch({ type: 'reset' }), [])
  const goToPage = useCallback(
    (page) => dispatch({ type: 'page', page }), [])

  const page = results.data
  const providers = page?.content ?? []
  const total = page?.totalElements ?? 0

  const activeFilterCount = useMemo(
    () => Object.entries(filters)
      .filter(([k, v]) => k !== 'page' && v !== '' && v !== false).length,
    [filters]
  )

  return (
    <main className="page">
      <div className="page__head">
        <h1>Find a provider in Florida</h1>
        <p>
          Every listing shows whether that provider is taking new clients
          right now, and how long the wait usually is.
        </p>
      </div>

      {catalog.loading && <Spinner label="Loading filters" />}
      {catalog.error && <Notice tone="error">{catalog.error.message}</Notice>}

      {catalog.data && (
        <SearchFilters
          filters={filters}
          catalog={catalog.data}
          onChange={onChange}
          onToggle={onToggle}
          onReset={onReset}
        />
      )}

      {results.error && <Notice tone="error">{results.error.message}</Notice>}

      {results.loading ? (
        <Spinner label="Searching" />
      ) : providers.length === 0 ? (
        <Empty title="No providers match those filters">
          <p>
            Try removing the insurance filter first - it narrows the
            list the most.
          </p>
          {activeFilterCount > 0 && (
            <Button variant="quiet" onClick={onReset}>Clear all filters</Button>
          )}
        </Empty>
      ) : (
        <>
          <p className="muted small" aria-live="polite">
            {total} {total === 1 ? 'provider' : 'providers'}
            {activeFilterCount > 0 && ' matching your filters'}
          </p>

          {providers.map((p) => <ProviderCard key={p.id} provider={p} />)}

          {page.totalPages > 1 && (
            <div className="row row--end">
              <Button
                variant="quiet"
                size="small"
                disabled={page.first}
                onClick={() => goToPage(filters.page - 1)}
              >
                Previous
              </Button>
              <span className="small muted">
                Page {page.number + 1} of {page.totalPages}
              </span>
              <Button
                variant="quiet"
                size="small"
                disabled={page.last}
                onClick={() => goToPage(filters.page + 1)}
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}

      <p className="sample-note">
        Provider listings in this directory are sample data for a course
        project. Every organization name begins with "Example". The{' '}
        <Link to="/resources">statewide resources</Link> page lists real
        helplines.
      </p>
    </main>
  )
}
